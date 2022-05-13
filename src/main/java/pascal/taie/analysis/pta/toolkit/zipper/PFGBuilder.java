/*
 * Tai-e: A Static Analysis Framework for Java
 *
 * Copyright (C) 2022 Tian Tan <tiantan@nju.edu.cn>
 * Copyright (C) 2022 Yue Li <yueli@nju.edu.cn>
 *
 * This file is part of Tai-e.
 *
 * Tai-e is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation, either version 3
 * of the License, or (at your option) any later version.
 *
 * Tai-e is distributed in the hope that it will be useful,but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with Tai-e. If not, see <https://www.gnu.org/licenses/>.
 */

package pascal.taie.analysis.pta.toolkit.zipper;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import pascal.taie.analysis.pta.core.heap.Obj;
import pascal.taie.analysis.pta.toolkit.PointerAnalysisResultEx;
import pascal.taie.ir.exp.Var;
import pascal.taie.ir.stmt.Invoke;
import pascal.taie.ir.stmt.New;
import pascal.taie.language.classes.JClass;
import pascal.taie.language.classes.JMethod;
import pascal.taie.language.type.ClassType;
import pascal.taie.language.type.Type;
import pascal.taie.util.collection.Maps;
import pascal.taie.util.collection.MultiMap;
import pascal.taie.util.collection.Sets;
import pascal.taie.util.collection.Views;
import pascal.taie.util.graph.Graph;
import pascal.taie.util.graph.SimpleGraph;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static pascal.taie.analysis.pta.toolkit.zipper.OFGEdge.Kind.UNWRAPPED_FLOW;
import static pascal.taie.analysis.pta.toolkit.zipper.OFGEdge.Kind.WRAPPED_FLOW;

class PFGBuilder {

    private static final Logger logger = LogManager.getLogger(PFGBuilder.class);

    private final PointerAnalysisResultEx pta;

    private final ObjectFlowGraph ofg;

    private final ObjectAllocationGraph oag;

    private final PotentialContextElement pce;

    /**
     * The input type.
     */
    private final Type type;

    /**
     * Methods invoked on objects of the input type.
     */
    private final Set<JMethod> invokeMethods;

    /**
     * The current precision flow graph being built.
     */
    private PrecisionFlowGraph pfg;

    /**
     * Stores wrapped and unwrapped flow edges.
     */
    private MultiMap<OFGNode, OFGEdge> wuEdges;

    private Set<OFGNode> visitedNodes;

    private Set<VarNode> inNodes;

    private Set<VarNode> outNodes;

    PFGBuilder(PointerAnalysisResultEx pta, ObjectFlowGraph ofg,
               ObjectAllocationGraph oag, PotentialContextElement pce,
               Type type) {
        this.pta = pta;
        this.ofg = ofg;
        this.oag = oag;
        this.pce = pce;
        this.type = type;
        this.invokeMethods = pta.getObjectsOf(type)
            .stream()
            .map(pta::getMethodsInvokedOn)
            .flatMap(Set::stream)
            .collect(Collectors.toUnmodifiableSet());
    }

    Graph<OFGNode> build() {
        pfg = new PrecisionFlowGraph();
        wuEdges = Maps.newMultiMap();
        visitedNodes = Sets.newSet();
        inNodes = obtainInNodes();
        outNodes = obtainOutNodes();
        for (VarNode inNode : inNodes) {
            dfs(inNode);
        }
        return pfg;
    }

    private Set<JMethod> obtainMethods() {
        return pta.getObjectsOf(type)
            .stream()
            .map(pta::getMethodsInvokedOn)
            .flatMap(Set::stream)
            .filter(Predicate.not(JMethod::isPrivate))
            .collect(Collectors.toUnmodifiableSet());
    }

    private Set<VarNode> obtainInNodes() {
        return obtainMethods()
            .stream()
            .flatMap(method -> method.getIR().getParams().stream())
            .filter(param -> !pta.getBase().getPointsToSet(param).isEmpty())
            .map(ofg::getVarNode)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
    }

    private Set<VarNode> obtainOutNodes() {
        Set<JMethod> outMethods = new HashSet<>(obtainMethods());
        // OUT methods of inner classes and special access$ methods
        // are also considered as the OUT methods of current type
        pce.PCEMethodsOf(type)
            .stream()
            .filter(m -> !m.isPrivate() && !m.isStatic())
            .filter(m -> isInnerClass(m.getDeclaringClass()))
            .forEach(outMethods::add);
        pce.PCEMethodsOf(type)
            .stream()
            .filter(m -> !m.isPrivate() && m.isStatic())
            .filter(m -> m.getDeclaringClass().getType().equals(type)
                && m.getName().startsWith("access$"))
            .forEach(outMethods::add);
        return outMethods.stream()
            .flatMap(method -> method.getIR().getReturnVars().stream())
            .filter(ret -> !pta.getBase().getPointsToSet(ret).isEmpty())
            .map(ofg::getVarNode)
            .filter(Objects::nonNull)
            .collect(Collectors.toUnmodifiableSet());
    }

    private boolean isInnerClass(JClass jclass) {
        if (type instanceof ClassType classType) {
            JClass outer = classType.getJClass();
            do {
                JClass inner = jclass;
                while (inner != null && !inner.equals(outer)) {
                    if (Objects.equals(inner.getOuterClass(), outer)) {
                        return true;
                    }
                    inner = inner.getOuterClass();
                }
                outer = outer.getSuperClass();
            } while (outer != null);
        }
        return false;
    }

    private void dfs(OFGNode node) {
        logger.info("dfs on {}", node);
        if (visitedNodes.contains(node)) {
            return;
        }
        visitedNodes.add(node);
        pfg.addNode(node);
        // add unwrapped flow edges
        if (node instanceof VarNode varNode) {
            Var var = varNode.getVar();
            Set<Obj> varPts = pta.getBase().getPointsToSet(var);
            // Optimization: approximate unwrapped flows to make
            // Zipper and pointer analysis run faster
            getReturnToVariablesOf(var).forEach(toVar -> {
                VarNode toNode = ofg.getVarNode(toVar);
                if (toNode != null && outNodes.contains(toNode)) {
                    for (VarNode inNode : inNodes) {
                        Var inVar = inNode.getVar();
                        if (!Collections.disjoint(
                            pta.getBase().getPointsToSet(inVar), varPts)) {
                            wuEdges.put(node, new OFGEdge(UNWRAPPED_FLOW, node, toNode));
                            break;
                        }
                    }
                }
            });
        }
        List<OFGEdge> nextEdges = new ArrayList<>();
        for (OFGEdge edge : getOutEdgesOf(node)) {
            switch (edge.kind()) {
                case LOCAL_ASSIGN, UNWRAPPED_FLOW -> {
                    nextEdges.add(edge);
                }
                case INTERPROCEDURAL_ASSIGN, INSTANCE_LOAD, WRAPPED_FLOW -> {
                    // target node must be a VarNode
                    VarNode toNode = (VarNode) edge.getTarget();
                    Var toVar = toNode.getVar();
                    // Optimization: filter out some potential spurious flows due to
                    // the imprecision of context-insensitive pre-analysis, which
                    // helps improve the performance of Zipper and pointer analysis.
                    if (pce.PCEMethodsOf(type).contains(toVar.getMethod())) {
                        nextEdges.add(edge);
                    }
                }
                case INSTANCE_STORE -> {
                    InstanceFieldNode toNode = (InstanceFieldNode) edge.getTarget();
                    Obj base = toNode.getBase();
                    if (base.getType().equals(type)) {
                        // add wrapped flow edges to this variable
                        invokeMethods.stream()
                            .map(m -> m.getIR().getThis())
                            .map(ofg::getVarNode)
                            .filter(Objects::nonNull) // filter this variable of native methods
                            .forEach(nextNode -> wuEdges.put(toNode,
                                new OFGEdge(WRAPPED_FLOW, toNode, nextNode)));
                        nextEdges.add(edge);
                    } else if (oag.getAllocateesOf(type).contains(base)) {
                        // Optimization, similar as above.
                        VarNode assignedNode = getAssignedNode(base);
                        if (assignedNode != null) {
                            wuEdges.put(toNode,
                                new OFGEdge(WRAPPED_FLOW, toNode, assignedNode));
                        }
                        nextEdges.add(edge);
                    }
                }
            }
        }
        for (OFGEdge nextEdge : nextEdges) {
            pfg.addEdge(nextEdge);
            dfs(nextEdge.target());
        }
    }

    public Set<OFGEdge> getOutEdgesOf(OFGNode node) {
        Set<OFGEdge> outEdges = ofg.getOutEdgesOf(node);
        if (wuEdges.containsKey(node)) {
            outEdges = new HashSet<>(outEdges);
            outEdges.addAll(wuEdges.get(node));
        }
        return outEdges;
    }

    private @Nullable VarNode getAssignedNode(Obj obj) {
        if (obj.getAllocation() instanceof New newStmt) {
            Var lhs = newStmt.getLValue();
            return ofg.getVarNode(lhs);
        }
        return null;
    }

    private static List<Var> getReturnToVariablesOf(Var var) {
        return var.getInvokes()
            .stream()
            .map(Invoke::getLValue)
            .filter(Objects::nonNull)
            .collect(Collectors.toList());
    }

    private static class PrecisionFlowGraph extends SimpleGraph<OFGNode> {

        private final MultiMap<OFGNode, OFGEdge> outEdges = Maps.newMultiMap();

        void addEdge(OFGEdge edge) {
            addNode(edge.source());
            addNode(edge.target());
            outEdges.put(edge.source(), edge);
        }

        @Override
        public Set<OFGEdge> getOutEdgesOf(OFGNode node) {
            return outEdges.get(node);
        }

        @Override
        public Set<OFGNode> getSuccsOf(OFGNode node) {
            return Views.toMappedSet(getOutEdgesOf(node), OFGEdge::target);
        }
    }
}