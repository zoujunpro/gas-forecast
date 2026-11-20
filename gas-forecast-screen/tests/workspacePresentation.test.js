import test from 'node:test'
import assert from 'node:assert/strict'
import {workspaceCopy} from '../src/workspacePresentation.js'
test('production wording preserves source limitations instead of asserting successful model calls',()=>{
 const source='本地示例，未调用真实模型。模拟回测，未运行真实训练。曲线不代表真实运行结果。'
 const text=workspaceCopy(source)
 assert.doesNotMatch(text,/模拟|演示|示例/)
 assert.match(text,/模型调用：未执行/);assert.match(text,/训练状态：未执行/);assert.match(text,/不作为已运行模型的输出/)
 assert.equal(source,'本地示例，未调用真实模型。模拟回测，未运行真实训练。曲线不代表真实运行结果。')
})
test('neutral wording keeps real source identity and pending service states intact',()=>{
 const actual='relationships.csv 第 16 行，数据截止 2026-11-18；待解析，服务未连接。'
 assert.equal(workspaceCopy(actual),actual)
 assert.equal(workspaceCopy(null),'')
})
