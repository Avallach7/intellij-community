/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.svn.auth;

import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.MessageType;
import com.intellij.openapi.ui.popup.util.PopupUtil;
import com.intellij.openapi.util.Getter;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.WaitForProgressToShow;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.net.HttpConfigurable;
import com.intellij.util.proxy.CommonProxy;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.SvnBundle;
import org.jetbrains.idea.svn.SvnConfiguration;
import org.jetbrains.idea.svn.SvnVcs;
import org.jetbrains.idea.svn.dialogs.SimpleCredentialsDialog;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.auth.SVNAuthentication;

import java.io.File;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 2/26/13
 * Time: 1:27 PM
 */
public class AuthenticationService {

  @NotNull private final SvnVcs myVcs;
  private final boolean myIsActive;
  private static final Logger LOG = Logger.getInstance(AuthenticationService.class);
  private File myTempDirectory;
  private boolean myProxyCredentialsWereReturned;
  private SvnConfiguration myConfiguration;
  private final Set<String> myRequestedCredentials;

  public AuthenticationService(@NotNull SvnVcs vcs, boolean isActive) {
    myVcs = vcs;
    myIsActive = isActive;
    myConfiguration = SvnConfiguration.getInstance(myVcs.getProject());
    myRequestedCredentials = ContainerUtil.newHashSet();
  }

  @NotNull
  public SvnVcs getVcs() {
    return myVcs;
  }

  @Nullable
  public File getTempDirectory() {
    return myTempDirectory;
  }

  public boolean isActive() {
    return myIsActive;
  }

  @Nullable
  public SVNAuthentication requestCredentials(final SVNURL repositoryUrl, final String type) {
    SVNAuthentication authentication = null;

    if (repositoryUrl != null) {
      final String realm = repositoryUrl.toDecodedString();

      authentication = requestCredentials(realm, type, new Getter<SVNAuthentication>() {
        @Override
        public SVNAuthentication get() {
          return myVcs.getSvnConfiguration().getInteractiveManager(myVcs).getInnerProvider()
            .requestClientAuthentication(type, repositoryUrl, realm, null, null, true);
        }
      });
    }

    if (authentication == null) {
      LOG.warn("Could not get authentication. Type - " + type + ", Url - " + repositoryUrl);
    }

    return authentication;
  }

  @Nullable
  private <T> T requestCredentials(@NotNull String realm, @NotNull String type, @NotNull Getter<T> fromUserProvider) {
    T result = null;
    // Search for stored credentials not only by key but also by "parent" keys. This is useful when we work just with URLs
    // (not working copy) and can't detect repository url beforehand because authentication is required. If found credentials of "parent"
    // are not correct then current key will already be stored in myRequestedCredentials - thus user will be asked for credentials and
    // provided result will be stored in cache (with necessary key).
    Object data = SvnConfiguration.RUNTIME_AUTH_CACHE.getDataWithLowerCheck(type, realm);
    String key = SvnConfiguration.AuthStorage.getKey(type, realm);

    // we return credentials from cache if they are asked for the first time during command execution, otherwise - user is asked
    if (data != null && !myRequestedCredentials.contains(key)) {
      // we already have credentials in memory cache
      result = (T)data;
      myRequestedCredentials.add(key);
    }
    else if (myIsActive) {
      // ask user for credentials
      result = fromUserProvider.get();
      if (result != null) {
        // save user credentials to memory cache
        myVcs.getSvnConfiguration().acknowledge(type, realm, result);
        myRequestedCredentials.add(key);
      }
    }

    return result;
  }

  @Nullable
  public String requestSshCredentials(@NotNull final String realm,
                                      @NotNull final SimpleCredentialsDialog.Mode mode,
                                      @NotNull final String key) {
    return requestCredentials(realm, ISVNAuthenticationManager.SSH, new Getter<String>() {
      @Override
      public String get() {
        final Ref<String> answer = new Ref<String>();

        Runnable command = new Runnable() {
          public void run() {
            SimpleCredentialsDialog dialog = new SimpleCredentialsDialog(myVcs.getProject());

            dialog.setup(mode, realm, key, true);
            dialog.setTitle(SvnBundle.message("dialog.title.authentication.required"));
            dialog.setSaveEnabled(false);
            dialog.show();
            if (dialog.isOK()) {
              answer.set(dialog.getPassword());
            }
          }
        };

        // Use ModalityState.any() as currently ssh credentials in terminal mode are requested in the thread that reads output and not in
        // the thread that started progress
        WaitForProgressToShow.runOrInvokeAndWaitAboveProgress(command, ModalityState.any());

        return answer.get();
      }
    });
  }

  public boolean acceptSSLServerCertificate(final SVNURL repositoryUrl, final String realm) {
    if (repositoryUrl == null) {
      return false;
    }

    return new SSLServerCertificateAuthenticator(this, repositoryUrl, realm).tryAuthenticate();
  }

  public void clearPassiveCredentials(String realm, SVNURL repositoryUrl, boolean password) {
    if (repositoryUrl == null) {
      return;
    }

    final SvnConfiguration configuration = SvnConfiguration.getInstance(myVcs.getProject());
    final List<String> kinds = getKinds(repositoryUrl, password);

    for (String kind : kinds) {
      configuration.clearCredentials(kind, realm);
    }
  }

  public boolean haveDataForTmpConfig() {
    final HttpConfigurable instance = HttpConfigurable.getInstance();
    return SvnConfiguration.getInstance(myVcs.getProject()).isIsUseDefaultProxy() &&
           (instance.USE_HTTP_PROXY || instance.USE_PROXY_PAC);
  }

  @Nullable
  public static Proxy getIdeaDefinedProxy(@NotNull final SVNURL url) {
    // SVNKit authentication implementation sets repositories as noProxy() to provide custom proxy authentication logic - see for instance,
    // SvnAuthenticationManager.getProxyManager(). But noProxy() setting is not cleared correctly in all cases - so if svn command
    // (for command line) is executed on thread where repository url was added as noProxy() => proxies are not retrieved for such commands
    // and execution logic is incorrect.

    // To prevent such behavior repositoryUrl is manually removed from noProxy() list (for current thread).
    // NOTE, that current method is only called from code flows for executing commands through command line client and should not be called
    // from SVNKit code flows.
    CommonProxy.getInstance().removeNoProxy(url.getProtocol(), url.getHost(), url.getPort());

    final List<Proxy> proxies = CommonProxy.getInstance().select(URI.create(url.toString()));
    if (proxies != null && !proxies.isEmpty()) {
      for (Proxy proxy : proxies) {
        if (HttpConfigurable.isRealProxy(proxy) && Proxy.Type.HTTP.equals(proxy.type())) {
          return proxy;
        }
      }
    }
    return null;
  }

  @Nullable
  public PasswordAuthentication getProxyAuthentication(@NotNull SVNURL repositoryUrl) {
    Proxy proxy = getIdeaDefinedProxy(repositoryUrl);
    PasswordAuthentication result = null;

    if (proxy != null) {
      if (myProxyCredentialsWereReturned) {
        showFailedAuthenticateProxy();
      }
      else {
        result = getProxyAuthentication(proxy, repositoryUrl);
        myProxyCredentialsWereReturned = result != null;
      }
    }

    return result;
  }

  private static void showFailedAuthenticateProxy() {
    HttpConfigurable instance = HttpConfigurable.getInstance();
    String message = instance.USE_HTTP_PROXY || instance.USE_PROXY_PAC
                     ? "Failed to authenticate to proxy. You can change proxy credentials in HTTP proxy settings."
                     : "Failed to authenticate to proxy.";

    PopupUtil.showBalloonForActiveComponent(message, MessageType.ERROR);
  }

  @Nullable
  private static PasswordAuthentication getProxyAuthentication(@NotNull Proxy proxy, @NotNull SVNURL repositoryUrl) {
    PasswordAuthentication result = null;

    try {
      result = Authenticator.requestPasswordAuthentication(repositoryUrl.getHost(), ((InetSocketAddress)proxy.address()).getAddress(),
                                                           repositoryUrl.getPort(), repositoryUrl.getProtocol(), repositoryUrl.getHost(),
                                                           repositoryUrl.getProtocol(), new URL(repositoryUrl.toString()),
                                                           Authenticator.RequestorType.PROXY);
    }
    catch (MalformedURLException e) {
      LOG.info(e);
    }

    return result;
  }

  public void reset() {
    if (myTempDirectory != null) {
      FileUtil.delete(myTempDirectory);
    }
  }

  @NotNull
  public static List<String> getKinds(final SVNURL url, boolean passwordRequest) {
    if (passwordRequest || "http".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.PASSWORD);
    }
    else if ("https".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.SSL);
    }
    else if ("svn".equals(url.getProtocol())) {
      return Collections.singletonList(ISVNAuthenticationManager.PASSWORD);
    }
    else if (url.getProtocol().contains("svn+")) {  // todo +-
      return Arrays.asList(ISVNAuthenticationManager.SSH, ISVNAuthenticationManager.USERNAME);
    }
    return Collections.singletonList(ISVNAuthenticationManager.USERNAME);
  }

  @Nullable
  public File getSpecialConfigDir() {
    return myTempDirectory != null ? myTempDirectory : new File(myConfiguration.getConfigurationDirectory());
  }

  public void initTmpDir(SvnConfiguration configuration) throws IOException {
    if (myTempDirectory == null) {
      myTempDirectory = FileUtil.createTempDirectory("tmp", "Subversion");
      FileUtil.copyDir(new File(configuration.getConfigurationDirectory()), myTempDirectory);
    }
  }
}
