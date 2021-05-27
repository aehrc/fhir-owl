package au.csiro.fhir.owl.util;

import java.util.*;
import java.util.function.Function;

/**
 * Miscellaneous graph utilities.
 *
 * @author Alejandro Metke Jimenez
 */
public class GraphUtils {

  public static <M> void transitiveClosure(Set<M> nodes, Function<M,Set<M>> getEdges) {
    for (M node: nodes) {
      final Set<M> dependents = getEdges.apply(node);
      final Queue<M> queue = new LinkedList<>(dependents);

      while (!queue.isEmpty()) {
        final M key = queue.poll();
        if (!nodes.contains(key)) {
          continue;
        }
        for (M addition: getEdges.apply(key)) {
          if (!dependents.contains(addition)) {
            dependents.add(addition);
            queue.add(addition);
          }
        }
      }
    }
  }

  public static <M> void transitiveClosure(Map<M, Set<M>> index) {
    for (Map.Entry<M, Set<M>> entry: index.entrySet()) {
      final Set<M> dependents = entry.getValue();
      final Queue<M> queue = new LinkedList<>(dependents);

      while (!queue.isEmpty()) {
        final M key = queue.poll();
        if (!index.containsKey(key)) {
          continue;
        }
        for (M addition: index.get(key)) {
          if (!dependents.contains(addition)) {
            dependents.add(addition);
            queue.add(addition);
          }
        }
      }
    }
  }

  public static <T> Map<T, Set<T>> transitiveReduction(final Set<T> nodes, Function<T,Set<T>> getPaths) {
    // include all things that are children
    final Set<T> allNodes = new HashSet<>(nodes);
    // include all things that are parents (ancestors)
    nodes.forEach(n -> allNodes.addAll(getPaths.apply(n)));

    final TransitiveReduction<T> tr = new TransitiveReduction<>(allNodes) {
      @Override
      public Set<T> getAncestors(T id) {
        final Set<T> ancestors = getPaths.apply(id);
        return Objects.requireNonNullElse(ancestors, Collections.emptySet());
      }
    };

    return tr.parentMap;
  }

  public static abstract class TransitiveReduction<T> {

    private final Map<T, Set<T>> equivalentsMap = new HashMap<>();
    private final Map<T, Set<T>> ancestorMap = new HashMap<>();
    private final Map<T, Set<T>> parentMap = new HashMap<>();

    /**
     * Computes the transitive reduction of the specified set of nodes.
     *
     * @param nodeSet A collection of node sets.
     */
    public TransitiveReduction(final Collection<T> nodeSet) {
      for (final T id: nodeSet) {
        equivalentsMap.put(id, new HashSet<>());
        ancestorMap.put(id, new HashSet<>());
        parentMap.put(id, new HashSet<>());
      }

      final Set<T> processed = new HashSet<>();

      for (final T id: nodeSet) {
        if (!processed.contains(id)) {
          reduceConcept(processed, id);
        }
      }

      /*
       * Equivalents get skipped during reduction; we need to update their data
       * from the core equivalent concept, adjusting as necessary.
       */
      for (final T id: equivalentsMap.keySet()) {
        for (final T equiv: equivalentsMap.get(id)) {
          final Set<T> eAncestors = ancestorMap.get(equiv);
          eAncestors.addAll(ancestorMap.get(id));
          eAncestors.remove(equiv);
          eAncestors.add(id);

          final Set<T> eParents = parentMap.get(equiv);
          eParents.addAll(parentMap.get(id));
          eParents.remove(equiv);
          eParents.add(id);
        }
      }
    }

    /**
     * Override this method to provide the data to the transitive closure algorithm.
     * <p>
     * Note, <b>should not</b> return nodes other than those passed as the constructor's nodeSet parameter
     *
     * @param id The id of a node.
     * @return Set of ancestor ids or empty set if none.
     */
    public abstract Set<T> getAncestors(T id);

    private void reduceConcept(final Set<T> processed, final T concept) {

      if (null == concept) {
        throw new RuntimeException("Warning, null concept found during filtering.");
      }

      // This is really doing a self-join on S1.parent and S2.child
      // (A = S1.child, B = S1.parent = S2.child, check for A == S2.parent && A != B)
      //
      final Collection<T> conceptAncestors = getAncestors(concept);

      final Set<T> candidates = new HashSet<>();

      for (final T ancestor: conceptAncestors) {
        final Set<T> ancestorIds = ancestorMap.get(concept);

        if (!concept.equals(ancestor) && parentMap.containsKey(ancestor)) {
          ancestorIds.add(ancestor);
          if (getAncestors(ancestor).contains(concept)) {
            equivalentsMap.get(concept).add(ancestor);
            processed.add(ancestor);
            throw new RuntimeException("Cycle found in hierarchy between: " + concept + " and " + ancestor);
          } else {
            if (!processed.contains(ancestor)) {
              reduceConcept(processed, ancestor);
            }
            candidates.add(ancestor);
          }
        }
      }
      filterAncestors(concept, candidates);

      processed.add(concept);
    }

    private void filterAncestors(final T concept, final Set<T> candidateParents) {
      final Set<T> ancestors = new HashSet<>();
      for (final T maybeParent: candidateParents) {
        ancestors.addAll(parentMap.get(maybeParent));
      }

      candidateParents.removeAll(ancestors);  // after this they are knownParents

      if (!parentMap.containsKey(concept)) {
        throw new RuntimeException("Missing parent entry for: " + concept + " in parent map");
      }

      if (candidateParents.size() > 0) {      // if there are any parents
        parentMap.get(concept).addAll(candidateParents);
      }
    }

  }

}
