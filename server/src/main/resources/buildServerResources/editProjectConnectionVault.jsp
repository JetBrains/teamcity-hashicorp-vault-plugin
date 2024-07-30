<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include-internal.jsp" %>

<jsp:useBean id="keys" class="org.jetbrains.teamcity.vault.server.VaultJspKeys" />

<jsp:useBean id="project" type="jetbrains.buildServer.serverSide.SProject" scope="request" />
<jsp:useBean id="oauthConnectionBean" type="jetbrains.buildServer.serverSide.oauth.OAuthConnectionBean" scope="request" />
<jsp:useBean id="propertiesBean" type="jetbrains.buildServer.serverSide.oauth.OAuthConnectionBean" scope="request" />
<c:set var="gcpIamAuthEnabled" value="${intprop:getBoolean('teamcity.internal.vault.gcp.iam.enabled')}"/>
<bs:linkScript>
    /js/bs/testConnection.js
</bs:linkScript>
<style type="text/css">
    .auth-container {
        display: none;
    }

    .smallNote {
        word-break: break-word;
    }
</style>
<script>
    BS.OAuthConnectionDialog.submitTestConnection = function() {
        var that = this;
        BS.PasswordFormSaver.save(this, '<c:url value="/admin/hashicorp-vault-test-connection.html"/>', OO.extend(BS.ErrorsAwareListener, {
            onFailedTestConnectionError: function(elem) {
                var text = "";
                if (elem.firstChild) {
                    text = elem.firstChild.nodeValue;
                }
                BS.TestConnectionDialog.show(false, text, $('testConnectionButton'));
            },

            onCompleteSave: function(form, responseXML) {
                var err = BS.XMLResponse.processErrors(responseXML, this, form.propertiesErrorsHandler);
                BS.ErrorsAwareListener.onCompleteSave(form, responseXML, err);
                if (!err) {
                    this.onSuccessfulSave(responseXML);
                }
            },

            onSuccessfulSave: function(responseXML) {
                that.enable();

                var additionalInfo = "";
                var testConnectionResultNodes = responseXML.documentElement.getElementsByTagName("testConnectionResult");
                if (testConnectionResultNodes && testConnectionResultNodes.length > 0) {
                    var testConnectionResult = testConnectionResultNodes.item(0);
                    if (testConnectionResult.firstChild) {
                        additionalInfo = testConnectionResult.firstChild.nodeValue;
                    }
                }

                BS.TestConnectionDialog.show(true, additionalInfo, $('testConnectionButton'));
            }
        }));
        return false;
    };

    var afterClose = BS.OAuthConnectionDialog.afterClose;
    BS.OAuthConnectionDialog.afterClose = function() {
        $j('#OAuthConnectionDialog .testConnectionButton').remove();
        afterClose()
    }
</script>
<tr>
    <td><label for="${keys.DISPLAY_NAME}">Display name:</label>
        <l:star/>
    </td>
    <td>
        <props:textProperty name="${keys.DISPLAY_NAME}" className="longField" />
        <span class="smallNote">The public name of the connection</span>
        <span class="error" id="error_${keys.DISPLAY_NAME}"></span>
    </td>
</tr>
<tr>
    <c:choose>
        <c:when test="${not empty propertiesBean.properties[keys.NAMESPACE]}">
            <th><label for="${keys.NAMESPACE}">ID:</label></th>
            <td>
                <props:textProperty name="${keys.NAMESPACE}" className="longField" />
                <span class="error" id="error_${keys.NAMESPACE}"></span>
                <span class="smallNote">The ID can be used in TeamCity parameters and to identify this connection if multiple Vault connections exist</span>
            </td>
        </c:when>
        <c:otherwise>
            <c:set var="connectionId" value="${oauthConnectionBean.getConnectionId()}"/>
            <th><label for="${keys.CONNECTION_ID}">ID:</label></th>
            <td>
                <c:choose>
                    <c:when test = "${empty connectionId}">
                        <props:textProperty name="${keys.CONNECTION_ID}" className="longField" />
                        <span class="error" id="error_${keys.CONNECTION_ID}"></span>
                        <span class="smallNote">The ID can be used in TeamCity parameters and to identify this connection if multiple Vault connections exist</span>
                    </c:when>
                    <c:otherwise>
                        <label style="word-break: break-all;">${connectionId}</label>
                    </c:otherwise>
                </c:choose>
            </td>
        </c:otherwise>
    </c:choose>
</tr>
<tr>
    <td><label for="${keys.URL}">Vault URL:</label></td>
    <td>
        <props:textProperty name="${keys.URL}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.URL}" />
        <span class="smallNote">The Vault URL in the <code>https://&lt;vaultserver&gt;:&lt;port&gt;</code> format</span>
    </td>
</tr>

<tr class="advancedSetting">
    <td><label for="${keys.VAULT_NAMESPACE}">Vault Namespace:</label></td>
    <td>
        <props:textProperty name="${keys.VAULT_NAMESPACE}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.VAULT_NAMESPACE}" />
        <span class="smallNote">The name of the Vault Enterprise namespace</span>
    </td>
</tr>

<tr>
    <td>Authentication method</td>
    <td>
        <props:radioButtonProperty name="${keys.AUTH_METHOD}" id="${keys.AUTH_METHOD_APPROLE}" value="${keys.AUTH_METHOD_APPROLE}" onclick="BS.Vault.onAuthChange(this)" />
        <label for="${keys.AUTH_METHOD_APPROLE}">Use Vault AppRole</label>

        <br/>

        <props:radioButtonProperty name="${keys.AUTH_METHOD}" id="${keys.AUTH_METHOD_LDAP}" value="${keys.AUTH_METHOD_LDAP}" onclick="BS.Vault.onAuthChange(this)" />
        <label for="${keys.AUTH_METHOD_LDAP}">Use LDAP Auth</label>

        <br/>

        <c:if test="${gcpIamAuthEnabled}">
            <props:radioButtonProperty name="${keys.AUTH_METHOD}" id="${keys.AUTH_METHOD_GCP_IAM}" value="${keys.AUTH_METHOD_GCP_IAM}" onclick="BS.Vault.onAuthChange(this)" />
            <label for="${keys.AUTH_METHOD_GCP_IAM}">Use GCP IAM Auth</label>

            <br/>
        </c:if>

    </td>
</tr>

<tr class="advancedSetting auth-container auth-approle">
    <td><label for="${keys.ENDPOINT}">AppRole auth endpoint path:</label></td>
    <td>
        <props:textProperty name="${keys.ENDPOINT}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.ENDPOINT}" />
        <span class="smallNote">The path where the AppRole auth endpoint is mounted (for example, <code>approle</code>)</span>
    </td>
</tr>

<tr class="auth-container auth-approle">
    <td><label for="${keys.ROLE_ID}">AppRole Role ID:</label></td>
    <td>
        <props:textProperty name="${keys.ROLE_ID}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.ROLE_ID}" />
        <%--<span class="smallNote"></span>--%>
    </td>
</tr>

<tr class="noBorder auth-container auth-approle">
    <td><label for="${keys.SECRET_ID}">AppRole Secret ID:</label></td>
    <td>
        <props:passwordProperty name="${keys.SECRET_ID}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.SECRET_ID}" />
        <%--<span class="smallNote"></span>--%>
    </td>
</tr>

<tr class="auth-container auth-ldap">
    <td><label for="${keys.USERNAME}">Username</label></td>
    <td>
        <props:textProperty name="${keys.USERNAME}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.USERNAME}" />
        <%--<span class="smallNote"></span>--%>
    </td>
</tr>

<tr class="noBorder auth-container auth-ldap">
    <td><label for="${keys.PASSWORD}">Password</label></td>
    <td>
        <props:passwordProperty name="${keys.PASSWORD}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.PASSWORD}" />
        <%--<span class="smallNote"></span>--%>
    </td>
</tr>

<tr class="noBorder auth-container auth-ldap">
    <td><label for="${keys.PATH}">Path</label></td>
    <td>
        <props:textProperty name="${keys.PATH}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.PATH}" />
        <span class="smallNote">The path of the LDAP authentication backend mount. Defaults to "LDAP" if not set</span>
    </td>
</tr>

<tr class="auth-container auth-gcp-iam">
    <td><label for="${keys.GCP_ROLE}">GCP Role</label></td>
    <td>
        <props:textProperty name="${keys.GCP_ROLE}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.GCP_ROLE}" />
    </td>
</tr>

<tr class="noBorder auth-container auth-gcp-iam">
    <td><label for="${keys.GCP_SERVICE_ACCOUNT}">GCP Service Account ID</label></td>
    <td>
        <props:textProperty name="${keys.GCP_SERVICE_ACCOUNT}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.GCP_SERVICE_ACCOUNT}" />
        <span class="smallNote">
            Obtains the value from credentials data if not set, refer to
            <a target="_blank" rel="noopener noreferrer" href="https://cloud.google.com/docs/authentication/application-default-credentials">
                default GCP credentials
            </a>
        </span>
    </td>
</tr>

<tr class="noBorder auth-container auth-gcp-iam">
    <td><label for="${keys.GCP_ENDPOINT_PATH}">GCP Endpoint Path</label></td>
    <td>
        <props:textProperty name="${keys.GCP_ENDPOINT_PATH}" className="longField textProperty_max-width js_max-width" />
        <span class="error" id="error_${keys.GCP_ENDPOINT_PATH}" />
        <span class="smallNote">The path of the GCP authentication backend mount</span>
    </td>
</tr>

<tr>
    <td><label for="${keys.FAIL_ON_ERROR}">Fail in case of error</label></td>
    <td>
        <props:checkboxProperty name="${keys.FAIL_ON_ERROR}" />
        <span class="error" id="error_${keys.FAIL_ON_ERROR}" />
        <span class="smallNote">Check this option if errors in resolving parameter values should fail the build</span>
    </td>
</tr>

<props:hiddenProperty name="projectId" value="${project.externalId}" id="vaultProjectId"/>
<props:hiddenProperty name="connectionFeatureId" value="${oauthConnectionBean.connectionId}"/>

<forms:button id="vaultTestConnectionButton" className="testConnectionButton"
              onclick="return BS.OAuthConnectionDialog.submitTestConnection();">Test Connection</forms:button>
<bs:dialog dialogId="testConnectionDialog" title="Test Connection" closeCommand="BS.TestConnectionDialog.close();" closeAttrs="showdiscardchangesmessage='false'">
    <div id="testConnectionStatus"></div>
    <div id="testConnectionDetails" class="mono"></div>
</bs:dialog>
<script>
    $j('#OAuthConnectionDialog .popupSaveButtonsBlock .testConnectionButton').remove();
    $j("#vaultTestConnectionButton").appendTo($j('#OAuthConnectionDialog .popupSaveButtonsBlock')[0]);
    BS.Vault = {
        onAuthChange: function(element) {
            $j('.auth-container').hide();
            let value = $j(element).val();
            $j('.auth-' + value).show();
            BS.VisibilityHandlers.updateVisibility('mainContent');
        }
    };

    $j(document).ready(function() {
        BS.Vault.onAuthChange($j('input[name="prop:${keys.AUTH_METHOD}"]:checked'));
        BS.OAuthConnectionDialog.recenterDialog();
    })
</script>