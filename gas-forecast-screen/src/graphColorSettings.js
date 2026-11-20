import {spherePalette} from './graphAppearance.js'
const validColor=value=>typeof value==='string'&&/^#[0-9a-f]{6}$/i.test(value)
export const colorSettingsKey='wb-graph-colors-v1'
export function normalizeColorSettings(value){
  const typeColors=Object.fromEntries(Object.entries(value?.typeColors||{}).filter(([,color])=>validColor(color)).map(([key,color])=>[key,color.toLowerCase()]))
  // Keep saved type colors, but retire independent-node colors from older versions.
  return {mode:'type',typeColors}
}
export function typeColor(type,settings){return settings.typeColors[type]||spherePalette[type]?.[1]||'#607da6'}
export function nodeColor(node,settings){return typeColor(node.type,settings)}
