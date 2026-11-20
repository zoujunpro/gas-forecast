// The second color is the type's default fill; browser-saved custom colors take precedence.
export const spherePalette={
  企业:['#dbeafe','#2563eb'],品类:['#ffedd5','#ef9218'],地点:['#ede9fe','#8b5acf'],行业:['#dcfce7','#16a34a'],行业工厂:['#fce7f3','#db2777'],
  因素指标:['#e0e7ff','#4f46e5'],指标值:['#cffafe','#0891b2'],时间:['#ffe4e6','#c95a76'],事件:['#fee2e2','#e75a41'],预测:['#e0e7ff','#6366ca'],驱动因素:['#d1fae5','#0f9c8e'],需求变化:['#fef3c7','#d9971c'],业务行动:['#f3e8ff','#a85fc2'],传导规则:['#e2e8f0','#60758a'],
}
export function nodeLabelStyle(node,zoom){
  const overview=node.data.overview===true
  // Local neighborhoods have names below each circle. Dense overview reveals
  // labels only once nodes are large enough on screen; tooltips remain available.
  const label=overview?node.data.diameter*zoom>=45&&zoom>=.8:zoom>=.55
  return {label,labelText:node.data.name,labelFontSize:12,labelMaxWidth:130,labelMaxLines:2}
}
