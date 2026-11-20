// An exploration trail is a sequence of real graph hops, not browsing history.
export function explorationTrail(trail, target, {extend = false, edges = []} = {}) {
  if (!extend || !trail.length) return [target]
  const existing = trail.indexOf(target)
  if (existing >= 0) return trail.slice(0, existing + 1)
  const origin = trail.at(-1), adjacency = new Map()
  for (const edge of edges) {
    for (const [a,b] of [[edge.source,edge.target],[edge.target,edge.source]]) {
      if (!adjacency.has(a)) adjacency.set(a, [])
      adjacency.get(a).push(b)
    }
  }
  const previous = new Map([[origin, null]]), queue = [origin]
  for (let index = 0; index < queue.length && !previous.has(target); index++) {
    for (const id of adjacency.get(queue[index]) || []) {
      if (previous.has(id)) continue
      previous.set(id, queue[index]); queue.push(id)
    }
  }
  if (!previous.has(target)) return [target]
  const hops = []
  for (let id = target; id !== origin; id = previous.get(id)) hops.unshift(id)
  return [...trail, ...hops].slice(-8)
}
