package com.intellij.openapi.externalSystem.service.project;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.externalSystem.model.project.*;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;

import static com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil.*;

/**
 * @author Denis Zhdanov
 * @since 1/26/12 11:54 AM
 */
public class PlatformFacadeImpl implements PlatformFacade {

  @NotNull
  @Override
  public LibraryTable getProjectLibraryTable(@NotNull Project project) {
    return LibraryTablesRegistrar.getInstance().getLibraryTable(project);
  }

  @NotNull
  @Override
  public Collection<Module> getModules(@NotNull Project project) {
    return Arrays.asList(ModuleManager.getInstance(project).getModules());
  }

  @NotNull
  @Override
  public Collection<Module> getModules(@NotNull Project project, @NotNull final ProjectData projectData) {
    return ContainerUtil.filter(getModules(project), new Condition<Module>() {
      @Override
      public boolean value(Module module) {
        return isExternalSystemAwareModule(projectData.getOwner(), module) &&
               StringUtil.equals(projectData.getLinkedExternalProjectPath(), getExternalRootProjectPath(module));
      }
    });
  }

  @NotNull
  @Override
  public Collection<OrderEntry> getOrderEntries(@NotNull Module module) {
    return Arrays.asList(ModuleRootManager.getInstance(module).getOrderEntries());
  }

  @NotNull
  @Override
  public String getLocalFileSystemPath(@NotNull VirtualFile file) {
    return ExternalSystemApiUtil.getLocalFileSystemPath(file);
  }

  @Override
  public Module newModule(Project project, @NotNull @NonNls final String filePath, final String moduleTypeId) {
    final ModuleManager moduleManager = ModuleManager.getInstance(project);
    Module module = doWriteAction(new Computable<Module>() {
      @Override
      public Module compute() {
        return moduleManager.newModule(filePath, moduleTypeId);
      }
    });
    // set module type id explicitly otherwise it can not be set if there is an existing module (with the same filePath) and w/o 'type' attribute
    module.setOption(Module.ELEMENT_TYPE, moduleTypeId);
    return module;
  }

  @Override
  public ModifiableRootModel getModuleModifiableModel(Module module) {
    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    return ApplicationManager.getApplication().runReadAction(new Computable<ModifiableRootModel>() {
      @Override
      public ModifiableRootModel compute() {
        return moduleRootManager.getModifiableModel();
      }
    });
  }

  @Nullable
  @Override
  public Module findIdeModule(@NotNull ModuleData module, @NotNull Project ideProject) {
    final Module ideModule = findIdeModule(module.getInternalName(), ideProject);
    return isExternalSystemAwareModule(module.getOwner(), ideModule) ? ideModule : null;
  }

  @Nullable
  @Override
  public Module findIdeModule(@NotNull String ideModuleName, @NotNull Project ideProject) {
    for (Module module : getModules(ideProject)) {
      if (ideModuleName.equals(module.getName())) {
        return module;
      }
    }
    return null;
  }

  @Nullable
  @Override
  public Library findIdeLibrary(@NotNull final LibraryData libraryData, @NotNull Project ideProject) {
    final LibraryTable libraryTable = getProjectLibraryTable(ideProject);
    for (Library ideLibrary : libraryTable.getLibraries()) {
      if (isRelated(ideLibrary, libraryData)) return ideLibrary;
    }
    return null;
  }

  public boolean isOrphanProjectLibrary(@NotNull final Library library,
                                               @NotNull final Iterable<Module> ideModules) {
    RootPolicy<Boolean> visitor = new RootPolicy<Boolean>() {
      @Override
      public Boolean visitLibraryOrderEntry(LibraryOrderEntry ideDependency, Boolean value) {
        return !ideDependency.isModuleLevel() && library == ideDependency.getLibrary();
      }
    };
    for (Module module : ideModules) {
      for (OrderEntry entry : ModuleRootManager.getInstance(module).getOrderEntries()) {
        if (entry.accept(visitor, false)) return false;
      }
    }
    return true;
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Nullable
  @Override
  public ModuleOrderEntry findIdeModuleDependency(@NotNull ModuleDependencyData dependency, @NotNull ModifiableRootModel model) {
    for (OrderEntry entry : model.getOrderEntries()) {
      if (entry instanceof ModuleOrderEntry) {
        ModuleOrderEntry candidate = (ModuleOrderEntry)entry;
        if (dependency.getInternalName().equals(candidate.getModuleName()) &&
            dependency.getScope().equals(candidate.getScope())) {
          return candidate;
        }
      }
    }
    return null;
  }

  @Nullable
  @Override
  public OrderEntry findIdeModuleOrderEntry(LibraryDependencyData data, Project project) {
    Module ownerIdeModule = findIdeModule(data.getOwnerModule(), project);
    if (ownerIdeModule == null) return null;

    ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(ownerIdeModule);

    for (OrderEntry entry : moduleRootManager.getOrderEntries()) {
      if (entry instanceof LibraryOrderEntry) {
        if (((LibraryOrderEntry)entry).isModuleLevel() && data.getLevel() != LibraryLevel.MODULE) continue;
      }

      if (data.getInternalName().equals(entry.getPresentableName())) {
        return entry;
      }
    }
    return null;
  }

  @Override
  public void updateModule(@NotNull final Module module, @NotNull Consumer<ModifiableRootModel> task) {
    final ModifiableModelsProvider modifiableModelsProvider = ModifiableModelsProvider.SERVICE.getInstance();
    final ModifiableRootModel modifiableRootModel =
      ApplicationManager.getApplication().runReadAction(new Computable<ModifiableRootModel>() {
        @Override
        public ModifiableRootModel compute() {
          return modifiableModelsProvider.getModuleModifiableModel(module);
        }
      });
    try {
      task.consume(modifiableRootModel);
      if (modifiableRootModel.isChanged()) {
        doWriteAction(new Runnable() {
          @Override
          public void run() {
            modifiableModelsProvider.commitModuleModifiableModel(modifiableRootModel);
          }
        });
      }
      else {
        modifiableRootModel.dispose();
      }
    }
    catch (Throwable t) {
      modifiableModelsProvider.disposeModuleModifiableModel(modifiableRootModel);
      ExceptionUtil.rethrowAllAsUnchecked(t);
    }
  }

  @Override
  public void updateLibraryTable(@NotNull final Project project, @NotNull Consumer<LibraryTable.ModifiableModel> task) {
    final ModifiableModelsProvider modifiableModelsProvider = ModifiableModelsProvider.SERVICE.getInstance();
    final LibraryTable.ModifiableModel modifiableModel =
      ApplicationManager.getApplication().runReadAction(new Computable<LibraryTable.ModifiableModel>() {
        @Override
        public LibraryTable.ModifiableModel compute() {
          return modifiableModelsProvider.getLibraryTableModifiableModel(project);
        }
      });
    task.consume(modifiableModel);
    if (modifiableModel.isChanged()) {
      doWriteAction(new Runnable() {
        @Override
        public void run() {
          modifiableModel.commit();
        }
      });
    }
  }

  @Override
  public void updateLibrary(@NotNull final Library library, @NotNull Consumer<Library.ModifiableModel> task) {
    final Library.ModifiableModel modifiableModel = library.getModifiableModel();
    try {
      task.consume(modifiableModel);
      if (modifiableModel.isChanged()) {
      doWriteAction(new Runnable() {
        @Override
        public void run() {
          modifiableModel.commit();
        }
      });
      }
      else {
        Disposer.dispose(modifiableModel);
      }
    }
    catch (Throwable t) {
      Disposer.dispose(modifiableModel);
      ExceptionUtil.rethrowAllAsUnchecked(t);
    }
  }
}
