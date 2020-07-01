/*
 * Bamboo - A Program Analysis Framework for Java
 *
 * Copyright (C) 2020 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020 Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * This software is designed for the "Static Program Analysis" course at
 * Nanjing University, and it supports a subset of Java features.
 * Bamboo is only for educational and academic purposes, and any form of
 * commercial use is disallowed.
 */

package bamboo.pta.jimple;

import bamboo.pta.element.Method;
import bamboo.pta.element.Type;
import soot.ArrayType;
import soot.RefType;
import soot.SootClass;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

class JimpleType implements Type {

    private final soot.Type sootType;

    private SootClass sootClass;

    private Type superClass;

    private Set<Type> superInterfaces = Collections.emptySet();

    /**
     * If this type is array type, then elementType is the type of the
     * elements of the array.
     */
    private Type elementType;

    /**
     * If this type is array type, then baseType is the base type of the array.
     */
    private Type baseType;

    /**
     * Class initializer of this type.
     */
    private Method clinit;

    JimpleType(soot.Type sootType) {
        this.sootType = sootType;
    }

    @Override
    public Method getClassInitializer() {
        return clinit;
    }

    void setClassInitializer(Method clinit) {
        this.clinit = clinit;
    }

    void addSuperInterface(Type superInterface) {
        if (superInterfaces.isEmpty()) {
            superInterfaces = new HashSet<>(4);
        }
        superInterfaces.add(superInterface);
    }

    SootClass getSootClass() {
        return sootClass;
    }

    public void setSootClass(SootClass sootClass) {
        this.sootClass = sootClass;
    }

    soot.Type getSootType() {
        return sootType;
    }

    @Override
    public String getName() {
        return sootType.toString();
    }

    @Override
    public boolean isClassType() {
        return sootClass != null;
    }

    @Override
    public boolean isArrayType() {
        return sootType instanceof ArrayType;
    }

    @Override
    public Type getSuperClass() {
        return superClass;
    }

    void setSuperClass(Type superClass) {
        this.superClass = superClass;
    }

    @Override
    public Set<Type> getSuperInterfaces() {
        return superInterfaces;
    }

    @Override
    public Type getElementType() {
        return elementType;
    }

    void setElementType(Type elementType) {
        this.elementType = elementType;
    }

    @Override
    public Type getBaseType() {
        return baseType;
    }

    void setBaseType(Type baseType) {
        this.baseType = baseType;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        JimpleType that = (JimpleType) o;
        return sootType.equals(that.sootType);
    }

    @Override
    public int hashCode() {
        return sootType.hashCode();
    }

    @Override
    public String toString() {
        return sootType.toString();
    }
}
