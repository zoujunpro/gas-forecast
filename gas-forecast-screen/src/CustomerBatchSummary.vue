<template>
 <div class="bs"><div class="bs-heading"><h4>客户预测明细</h4><span>{{customers.filter(c=>c.status==='success').length}} / {{customers.length}} 个客户已生成</span></div><div class="bs-table"><table><thead><tr><th>客户</th><th>需求合计（万方）</th><th>峰值（万方）</th><th>状态</th></tr></thead><tbody><tr v-for="customer in customers" :key="customer.customerId"><td><b>{{customer.name}}</b><small>{{customer.region}}</small></td><td>{{number(customer.total)}}</td><td>{{number(customer.peak)}}</td><td>{{customer.status==='success'?'已生成':'缺少完整月销量'}}</td></tr></tbody></table></div></div>
</template>
<script setup>
defineProps({customers:Array})
const number=value=>value===null||value===undefined?'—':Number(value).toLocaleString('zh-CN',{minimumFractionDigits:2,maximumFractionDigits:2})
</script>
<style scoped>
.bs{margin:18px 0;border:1px solid var(--wb-ui-border);border-radius:8px;overflow:hidden}.bs-heading{display:flex;justify-content:space-between;align-items:center;gap:12px;padding:12px 14px;background:var(--wb-ui-surface)}.bs-heading h4{margin:0;font-size:var(--wb-font-meta);color:var(--wb-ui-text);font-weight:500}.bs-heading span{font-size:var(--wb-font-meta);color:var(--wb-ui-text)}.bs-table{max-height:250px;overflow:auto;scrollbar-width:thin}.bs table{width:100%;margin:0;border-collapse:collapse;font-size:var(--wb-font-meta);white-space:nowrap}.bs th{position:sticky;top:0;padding:10px 12px;text-align:left;background:var(--wb-ui-surface);font-size:var(--wb-font-meta);color:var(--wb-ui-muted);font-weight:400}.bs td{padding:10px 12px;border-top:1px solid var(--wb-ui-border);text-align:left;color:var(--wb-ui-secondary);font-variant-numeric:tabular-nums}.bs td b{display:block;color:var(--wb-ui-text);font-weight:400}.bs td small{display:block;margin-top:3px;font-size:var(--wb-font-meta);color:var(--wb-ui-muted)}.bs th:nth-child(2),.bs th:nth-child(3),.bs td:nth-child(2),.bs td:nth-child(3){text-align:right}
</style>
