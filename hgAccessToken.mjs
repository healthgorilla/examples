import crypto  from 'crypto';
import fetch from 'node-fetch';
import fs from "fs";

if (process.argv.length != 3) {
    console.log('Missing parameters. Call this script like that:');
    console.log('          node hgAccessToken _config');
    console.log('where _config references a configuration in the hgOauthConfig.json file.');
    process.exit();
  }
var config = JSON.parse(fs.readFileSync('hgOauthConfig.json'))[process.argv[2].toLowerCase()];
//
var now = new Date();
var iat = (now.getTime() - now.getMilliseconds()) / 1000;
now.setFullYear(now.getFullYear() + 1);
var exp = (now.getTime() - now.getMilliseconds()) / 1000;
var header = {
    "alg": "HS256",
    "typ": "JWT"
};
var payload = {
    "aud": config.envUrl,
    "iss": config.clientUrl,
    "sub": config.tenantUserLogin,
    "iat": iat,
    "exp": exp
  };

var headerEncoded = Buffer.from(JSON.stringify(header)).toString('base64url');
var payloadEncoded = Buffer.from(JSON.stringify(payload)).toString('base64url');
var hash = crypto.createHmac('sha256', config.clientSecret)
                   .update(headerEncoded + '.' + payloadEncoded)
                   .digest();
var hashEncoded = hash.toString('base64url');

var assertion =  headerEncoded + '.' + payloadEncoded + '.' + hashEncoded;
console.log(headerEncoded + '.' + payloadEncoded);

fetch(config.envUrl,
                {
                    method: 'POST',
                    headers: {'Content-Type': 'application/x-www-form-urlencoded'},
                    body: new URLSearchParams({
                        'grant_type': 'urn:ietf:params:oauth:grant-type:jwt-bearer',
                        'client_id': config.clientId,
                        'assertion': assertion,
                        'scope': config.scope
                    })
                })
    .then(resp => {
        console.log(resp.status);
        return resp.text();
    })
    .then(text => {
        console.log(text);
    });