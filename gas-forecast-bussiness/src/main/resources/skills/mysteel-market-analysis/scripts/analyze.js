#!/usr/bin/env node

const https = require('https');

function argument(name, alias) {
    const args = process.argv.slice(2);
    for (let index = 0; index < args.length; index += 1) {
        if ((args[index] === name || args[index] === alias) && index + 1 < args.length) {
            return String(args[index + 1]).trim();
        }
    }
    return '';
}

function apiKey() {
    const value = argument('--api_key', '--api-key') || String(process.env.MYSTEEL_API_KEY || '').trim();
    if (!value) {
        const error = new Error('MYSTEEL_API_KEY is not configured');
        error.isAuthError = true;
        throw error;
    }
    return value;
}

function requestAnalysis(query) {
    return new Promise((resolve, reject) => {
        let token;
        try {
            token = apiKey();
        } catch (error) {
            reject(error);
            return;
        }

        const payload = JSON.stringify({ query });
        const request = https.request({
            hostname: 'mcp.mysteel.com',
            port: 443,
            path: '/mcp/info/chat-robot/rag/answer',
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                token,
                'Content-Length': Buffer.byteLength(payload)
            },
            timeout: 90000
        }, (response) => {
            let body = '';
            response.on('data', (chunk) => { body += chunk; });
            response.on('end', () => {
                if (response.statusCode >= 400) {
                    reject(new Error(`Mysteel HTTP ${response.statusCode}: ${body.substring(0, 200)}`));
                    return;
                }
                try {
                    const result = JSON.parse(body);
                    if (!result.success) {
                        const error = new Error(`Mysteel API error[${result.code ?? 'N/A'}]: ${result.message || 'Unknown error'}`);
                        error.isAuthError = result.code === 400 || result.code === '400';
                        reject(error);
                        return;
                    }
                    if (!result.data) {
                        reject(new Error('Mysteel analysis report is empty'));
                        return;
                    }
                    resolve(result.data);
                } catch (error) {
                    reject(new Error(`Failed to parse Mysteel response: ${error.message}`));
                }
            });
        });

        request.on('timeout', () => request.destroy(new Error('Mysteel request timeout after 90 seconds')));
        request.on('error', reject);
        request.write(payload);
        request.end();
    });
}

async function analyze(query) {
    try {
        return await requestAnalysis(query);
    } catch (error) {
        if (error.isAuthError) throw error;
        return requestAnalysis(query);
    }
}

async function main() {
    const query = argument('--query');
    if (!query) {
        throw new Error('Usage: node scripts/analyze.js --query "市场分析问题"');
    }
    process.stdout.write(`${await analyze(query)}\n`);
}

main().catch((error) => {
    process.stderr.write(`${error.message}\n`);
    process.exit(1);
});
