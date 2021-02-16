# TeamCity Plugin for HashiCorp Vault

[![official JetBrains project](https://jb.gg/badges/official.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub) 
[![](https://teamcity.jetbrains.com/app/rest/builds/buildType:TeamCityPluginsByJetBrains_TeamCityHashiCorpVaultPlugin_Build/statusIcon.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=TeamCityPluginsByJetBrains_TeamCityHashiCorpVaultPlugin_Build)

The plugin allows connecting TeamCity to Vault, requesting new credentials when a build starts, passing them to the build script, and revoking them immediately when the build finishes.

See [blog post](https://blog.jetbrains.com/teamcity/2017/09/vault/) for details.

Download binaries in [Plugin repository](https://plugins.jetbrains.com/plugin/10011-hashicorp-vault-support).

## Configure with AWS IAM auth method
If both your TeamCity and Vault installations run on AWS EC2 instances, it is possible to use AWS IAM Auth method to authenticate to Vault. To enable this authentication method, check `AWS IAM Auth method` in the connection parameter screen.

It would even work if only TeamCity agents and Vault are on AWS EC2 instances, TeamCity server may be deployed elsewhere. Though in such case 'Test Connection' won't work.
  
![Vault connector](doc/aws_iam_auth.png)

**Notes**

***Server-side token revoke***

It's recommended to add folowing policy to approle, so TeamCity server will be able to revoke token 
even if TeamCity agent fails to do that on finishing build:
```hcl
path "auth/token/revoke-accessor" {
  capabilities = ["update"]
}
```
