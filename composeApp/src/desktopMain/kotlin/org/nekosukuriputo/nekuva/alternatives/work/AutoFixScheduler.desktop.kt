package org.nekosukuriputo.nekuva.alternatives.work

// Desktop has no background work scheduler; auto-fix runs only via the Alternatives screen.
actual fun scheduleAutoFix() = Unit
