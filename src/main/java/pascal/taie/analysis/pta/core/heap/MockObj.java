/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2020-- Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2020-- Yue Li <yueli@nju.edu.cn>
 * All rights reserved.
 *
 * Tai-e is only for educational and academic purposes,
 * and any form of commercial use is disallowed.
 * Distribution of Tai-e is disallowed without the approval.
 */

package pascal.taie.analysis.pta.core.heap;

import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.Type;
import pascal.taie.util.HashUtils;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents the objects whose allocation sites are not explicitly
 * written in the program.
 */
public class MockObj implements Obj {

    private final String descr;

    private final Object alloc;

    private final Type type;

    private final JMethod container;

    public MockObj(String descr, Object alloc, Type type, JMethod container) {
        this.descr = descr;
        this.alloc = alloc;
        this.type = type;
        this.container = container;
    }

    public MockObj(String descr, Object alloc, Type type) {
        this(descr, alloc, type, null);
    }

    public String getDescription() {
        return descr;
    }

    @Override
    public Type getType() {
        return type;
    }

    @Override
    public Object getAllocation() {
        return alloc;
    }

    @Override
    public Optional<JMethod> getContainerMethod() {
        return Optional.ofNullable(container);
    }

    @Override
    public Type getContainerType() {
        return container != null ?
                container.getDeclaringClass().getType() : type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        MockObj that = (MockObj) o;
        return descr.equals(that.descr) &&
                alloc.equals(that.alloc) &&
                type.equals(that.type);
    }

    @Override
    public int hashCode() {
        return HashUtils.safeHash(descr, alloc, type);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(descr).append('{');
        sb.append("alloc=").append(alloc).append(", ");
        sb.append("type=").append(type);
        if (container != null) {
            sb.append(" in ").append(container);
        }
        sb.append("}");
        return sb.toString();
    }
}
