package com.sun.tools.jscan;

import java.util.Set;

sealed interface RestrictedUse {
    record RestrictedMethodRefs(MethodRef referent, Set<MethodRef> referees) implements RestrictedUse {}
    record NativeMethodDecl(MethodRef decl) implements RestrictedUse {}
}
