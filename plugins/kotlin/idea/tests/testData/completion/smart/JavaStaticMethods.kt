fun foo(){
    val l : java.util.Calendar = <caret>
}

// EXIST: { lookupString:"getInstance", itemText:"Calendar.getInstance()", tailText:" (java.util)", typeText:"Calendar!" }
// EXIST: { lookupString:"getInstance", itemText:"Calendar.getInstance(TimeZone!)", tailText:" (java.util)", typeText:"Calendar!" }
// EXIST: { lookupString:"getInstance", itemText:"Calendar.getInstance(TimeZone!, Locale!)", tailText:" (java.util)", typeText:"Calendar!" }
