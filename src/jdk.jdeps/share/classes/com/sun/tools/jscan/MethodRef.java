package com.sun.tools.jscan;

import java.lang.classfile.MethodModel;
import java.lang.classfile.constantpool.MemberRefEntry;
import java.lang.constant.MethodTypeDesc;

record MethodRef(String methodName, MethodTypeDesc mtd) {
    public static MethodRef ofModel(MethodModel model) {
        return new MethodRef(model.methodName().stringValue(), model.methodTypeSymbol());
    }

    public static MethodRef ofMethodRef(MemberRefEntry method) {
        return new MethodRef(method.name().stringValue(), MethodTypeDesc.ofDescriptor(method.type().stringValue()));
    }

    @Override
    public String toString() {
        return methodName + mtd.displayDescriptor();
    }
}
