/* ==========================================
 * JGraphT : a free Java graph-theory library
 * ==========================================
 *
 * Project Info:  http://jgrapht.sourceforge.net/
 * Project Creator:  Barak Naveh (http://sourceforge.net/users/barak_naveh)
 *
 * (C) Copyright 2003-2015, by Barak Naveh and Contributors.
 *
 * This program and the accompanying materials are dual-licensed under
 * either
 *
 * (a) the terms of the GNU Lesser General Public License version 2.1
 * as published by the Free Software Foundation, or (at your option) any
 * later version.
 *
 * or (per the licensee's choosing)
 *
 * (b) the terms of the Eclipse Public License v1.0 as published by
 * the Eclipse Foundation.
 */
/* -----------------
 * CliqueMinimalSeparatorDecomposition.java
 * -----------------
 * (C) Copyright 2015, by Florian Buenzli and Contributors.
 *
 * Original Author:  Florian Buenzli
 * Contributor(s):   Thomas Tschager
 *                   Tomas Hruz
 *                   Philipp Hoppen
 *
 * $Id$
 *
 * Changes
 * -------
 * 06-Feb-2015 : Initial revision (FB);
 *
 */
package org.jgrapht.alg;

import java.util.*;
import java.util.Map.*;

import org.jgrapht.*;
import org.jgrapht.graph.*;


/**
 * Clique Minimal Separator Decomposition using MCS-M+ and Atoms algorithm as
 * described in Berry et al. An Introduction to Clique Minimal Separator
 * Decomposition (2010), DOI:10.3390/a3020197, <a
 * href="http://www.mdpi.com/1999-4893/3/2/197">
 * http://www.mdpi.com/1999-4893/3/2/197</a>
 *
 * <p>The Clique Minimal Separator (CMS) Decomposition is a procedure that
 * splits a graph into a set of subgraphs separated by minimal clique
 * separators, adding the separating clique to each component produced by the
 * separation. At the end we have a set of atoms. The CMS decomposition is
 * unique and yields the set of the atoms independent of the order of the
 * decomposition.
 *
 * @author Florian Buenzli (fbuenzli@student.ethz.ch)
 * @author Thomas Tschager (thomas.tschager@inf.ethz.ch)
 * @author Tomas Hruz (tomas.hruz@inf.ethz.ch)
 * @author Philipp Hoppen
 */
public class CliqueMinimalSeparatorDecomposition<V, E>
{


    /**
     * Source graph to operate on
     */
    private UndirectedGraph<V, E> graph;

    /**
     * Minimal triangulation of graph
     */
    private UndirectedGraph<V, E> chordalGraph;

    /**
     * Fill edges
     */
    private Set<E> fillEdges;

    /**
     * Minimal elimination ordering on the vertices of graph
     */
    private LinkedList<V> meo;

    /**
     * List of all vertices that generate a minimal separator of <code>
     * chordGraph</code>
     */
    private List<V> generators;

    /**
     * Set of clique minimal separators
     */
    private Set<Set<V>> separators;

    /**
     * The atoms generated by the decomposition
     */
    private Set<Set<V>> atoms;

    /**
     * Map for each separator how many components it produces.
     */
    private Map<Set<V>, Integer> fullComponentCount =
        new HashMap<Set<V>, Integer>();



    /**
     * Setup a clique minimal separator decomposition on undirected graph <code>
     * g</code>. Loops and multiple edges are removed, i.e. the graph is
     * transformed to a simple graph.
     *
     * @param g The graph to decompose.
     */
    public CliqueMinimalSeparatorDecomposition(UndirectedGraph<V, E> g)
    {
        this.graph = g;
        this.fillEdges = new HashSet<E>();
    }



    /**
     * Compute the minimal triangulation of the graph. Implementation of
     * Algorithm MCS-M+ as described in Berry et al. (2010),
     * DOI:10.3390/a3020197 <a href="http://www.mdpi.com/1999-4893/3/2/197">
     * http://www.mdpi.com/1999-4893/3/2/197</a>
     */
    private void computeMinimalTriangulation()
    {
        // initialize chordGraph with same vertices as graph
        chordalGraph = new SimpleGraph<V, E>(graph.getEdgeFactory());
        for (V v : graph.vertexSet()) {
            chordalGraph.addVertex(v);
        }

        // initialize g' as subgraph of graph (same vertices and edges)
        final UndirectedGraph<V, E> gprime = copyAsSimpleGraph(graph);
        int s = -1;
        generators = new ArrayList<V>();
        meo = new LinkedList<V>();

        final Map<V, Integer> vertexLabels = new HashMap<V, Integer>();
        for (V v : gprime.vertexSet()) {
            vertexLabels.put(v, 0);
        }
        for (int i = 1, n = graph.vertexSet().size(); i <= n; i++) {
            V v = getMaxLabelVertex(vertexLabels);
            LinkedList<V> Y =
                new LinkedList<V>(
                    Graphs.neighborListOf(gprime, v));

            if (vertexLabels.get(v) <= s) {
                generators.add(v);
            }

            s = vertexLabels.get(v);

            // Mark x reached and all other vertices of gprime unreached
            HashSet<V> reached = new HashSet<V>();
            reached.add(v);

            // mark neighborhood of x reached and add to reach(label(y))
            HashMap<Integer, HashSet<V>> reach =
                new HashMap<Integer, HashSet<V>>();

            // mark y reached and add y to reach
            for (V y : Y) {
                reached.add(y);
                addToReach(vertexLabels.get(y), y, reach);
            }

            for (int j = 0; j < graph.vertexSet().size(); j++) {
                if (!reach.containsKey(j)) {
                    continue;
                }
                while (reach.get(j).size() > 0) {
                    // remove a vertex y from reach(j)
                    V y = reach.get(j).iterator().next();
                    reach.get(j).remove(y);

                    for (V z : Graphs.neighborListOf(gprime, y)) {
                        if (!reached.contains(z)) {
                            reached.add(z);
                            if (vertexLabels.get(z) > j) {
                                Y.add(z);
                                E fillEdge =
                                    graph.getEdgeFactory().createEdge(
                                        v,
                                        z);
                                fillEdges.add(fillEdge);
                                addToReach(vertexLabels.get(z), z, reach);
                            } else {
                                addToReach(j, z, reach);
                            }
                        }
                    }
                }
            }

            for (V y : Y) {
                chordalGraph.addEdge(v, y);
                vertexLabels.put(y, vertexLabels.get(y) + 1);
            }

            meo.addLast(v);
            gprime.removeVertex(v);
            vertexLabels.remove(v);
        }
    }

    /**
     * Get the vertex with the maximal label.
     *
     * @param vertexLabels Map that gives a label for each vertex.
     *
     * @return Vertex with the maximal label.
     */
    private V getMaxLabelVertex(Map<V, Integer> vertexLabels)
    {
        Iterator<Entry<V, Integer>> iterator =
            vertexLabels.entrySet().iterator();
        Entry<V, Integer> max = iterator.next();
        while (iterator.hasNext()) {
            Entry<V, Integer> e = iterator.next();
            if (e.getValue() > max.getValue()) {
                max = e;
            }
        }
        return max.getKey();
    }

    /**
     * Add a vertex to reach.
     *
     * @param k vertex' label
     * @param v the vertex
     * @param r the reach structure.
     */
    private void addToReach(Integer k, V v, HashMap<Integer, HashSet<V>> r)
    {
        if (r.containsKey(k)) {
            r.get(k).add(v);
        } else {
            HashSet<V> set = new HashSet<V>();
            set.add(v);
            r.put(k, set);
        }
    }

    /**
     * Compute the unique decomposition of the input graph G (atoms of G).
     * Implementation of algorithm Atoms as described in Berry et al. (2010),
     * DOI:10.3390/a3020197, <a href="http://www.mdpi.com/1999-4893/3/2/197">
     * http://www.mdpi.com/1999-4893/3/2/197</a>
     */
    private void computeAtoms()
    {
        if (chordalGraph == null) {
            computeMinimalTriangulation();
        }

        separators = new HashSet<Set<V>>();

        // initialize g' as subgraph of graph (same vertices and edges)
        UndirectedGraph<V, E> gprime = copyAsSimpleGraph(graph);

        // initialize h' as subgraph of chordalGraph (same vertices and edges)
        UndirectedGraph<V, E> hprime = copyAsSimpleGraph(chordalGraph);

        atoms = new HashSet<Set<V>>();

        Iterator<V> iterator = meo.descendingIterator();
        while (iterator.hasNext()) {
            V v = iterator.next();
            if (generators.contains(v)) {
                Set<V> separator =
                    new HashSet<V>(Graphs.neighborListOf(
                            hprime,
                            v));

                if (isClique(graph, separator)) {
                    if (separator.size() > 0) {
                        if (separators.contains(separator)) {
                            fullComponentCount.put(
                                separator,
                                fullComponentCount.get(separator) + 1);
                        } else {
                            fullComponentCount.put(separator, 2);
                            separators.add(separator);
                        }
                    }
                    UndirectedGraph<V, E> tmpGraph = copyAsSimpleGraph(gprime);

                    tmpGraph.removeAllVertices(separator);
                    ConnectivityInspector<V, E> con =
                        new ConnectivityInspector<V, E>(tmpGraph);
                    if (con.isGraphConnected()) {
                        throw new RuntimeException(
                            "separator did not separate the graph");
                    }
                    for (Set<V> component : con.connectedSets()) {
                        if (component.contains(v)) {
                            gprime.removeAllVertices(component);
                            component.addAll(separator);
                            atoms.add(new HashSet<V>(component));
                            assert (component.size() > 0);
                            break;
                        }
                    }
                }
            }

            hprime.removeVertex(v);
        }

        if (gprime.vertexSet().size() > 0) {
            atoms.add(new HashSet<V>(gprime.vertexSet()));
        }
    }

    /**
     * Check whether the subgraph of <code>graph</code> induced by the given
     * <code>vertices</code> is complete, i.e. a clique.
     *
     * @param graph the graph.
     * @param vertices the vertices to induce the subgraph from.
     *
     * @return true if the induced subgraph is a clique.
     */
    private static <V, E> boolean isClique(
        UndirectedGraph<V, E> graph,
        Set<V> vertices)
    {
        for (V v1 : vertices) {
            for (V v2 : vertices) {
                if ((v1 != v2) && (graph.getEdge(v1, v2) == null)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Create a copy of a graph for internal use.
     *
     * @param graph the graph to copy.
     *
     * @return A copy of the graph projected to a SimpleGraph.
     */
    private static <V, E> UndirectedGraph<V, E> copyAsSimpleGraph(
        UndirectedGraph<V, E> graph)
    {
        UndirectedGraph<V, E> copy =
            new SimpleGraph<V, E>(
                graph.getEdgeFactory());

        if (graph instanceof SimpleGraph) {
            Graphs.addGraph(copy, graph);
        } else {
            // project graph to SimpleGraph
            Graphs.addAllVertices(copy, graph.vertexSet());
            for (E e : graph.edgeSet()) {
                V v1 = graph.getEdgeSource(e);
                V v2 = graph.getEdgeTarget(e);
                if ((v1 != v2) && !copy.containsEdge(e)) {
                    copy.addEdge(v1, v2);
                }
            }
        }
        return copy;
    }

    /**
     * Check if the graph is chordal.
     *
     * @return true if the graph is chordal, false otherwise.
     */
    public boolean isChordal()
    {
        if (chordalGraph == null) {
            computeMinimalTriangulation();
        }

        return (chordalGraph.edgeSet().size() == graph.edgeSet().size());
    }

    /**
     * Get the fill edges generated by the triangulation.
     *
     * @return Set of fill edges.
     */
    public Set<E> getFillEdges()
    {
        if (fillEdges == null) {
            computeMinimalTriangulation();
        }

        return fillEdges;
    }

    /**
     * Get the minimal triangulation of the graph.
     *
     * @return Triangulated graph.
     */
    public UndirectedGraph<V, E> getMinimalTriangulation()
    {
        if (chordalGraph == null) {
            computeMinimalTriangulation();
        }

        return chordalGraph;
    }

    /**
     * Get the generators of the separators of the triangulated graph, i.e. all
     * vertices that generate a minimal separator of triangulated graph.
     *
     * @return List of generators.
     */
    public List<V> getGenerators()
    {
        if (generators == null) {
            computeMinimalTriangulation();
        }

        return generators;
    }

    /**
     * Get the minimal elimination ordering produced by the triangulation.
     *
     * @return The minimal elimination ordering.
     */
    public LinkedList<V> getMeo()
    {
        if (meo == null) {
            computeMinimalTriangulation();
        }

        return meo;
    }

    /**
     * Get a map to know for each separator how many components it produces.
     *
     * @return A map from separators to integers (component count).
     */
    public Map<Set<V>, Integer> getFullComponentCount()
    {
        if (fullComponentCount == null) {
            computeAtoms();
        }

        return fullComponentCount;
    }

    /**
     * Get the atoms generated by the decomposition.
     *
     * @return Set of atoms, where each atom is described as the set of its
     * vertices.
     */
    public Set<Set<V>> getAtoms()
    {
        if (atoms == null) {
            computeAtoms();
        }

        return atoms;
    }

    /**
     * Get the clique minimal separators.
     *
     * @return Set of separators, where each separator is described as the set
     * of its vertices.
     */
    public Set<Set<V>> getSeparators()
    {
        if (separators == null) {
            computeAtoms();
        }

        return separators;
    }

    /**
     * Get the original graph.
     *
     * @return Original graph.
     */
    public UndirectedGraph<V, E> getGraph()
    {
        return graph;
    }
}

// End CliqueMinimalSeparatorDecomposition.java
