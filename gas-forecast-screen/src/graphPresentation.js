// Canvas rich-text tooltips do not interpret imported node values as HTML.
export const graphTooltip = (formatter) => ({
  renderMode: 'richText', confine: true, trigger: 'item', triggerOn: 'mousemove|click',
  backgroundColor: '#17283e', borderColor: '#3b526f', borderWidth: 1,
  padding: [14, 17], textStyle: { color: '#f4f7fc', fontSize: 12, lineHeight: 21 },
  formatter
})
export const wrapGraphText = (text, width = 28) => String(text || '').match(new RegExp(`.{1,${width}}`, 'gu'))?.join('\n') || ''
