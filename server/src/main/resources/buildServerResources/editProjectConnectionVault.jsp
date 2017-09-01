<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include-internal.jsp" %>
<jsp:useBean id="keys" class="org.jetbrains.teamcity.vault.server.VaultJspKeys"/>

<jsp:useBean id="project" type="jetbrains.buildServer.serverSide.SProject" scope="request"/>
<jsp:useBean id="oauthConnectionBean" type="jetbrains.buildServer.serverSide.oauth.OAuthConnectionBean"
             scope="request"/>
<jsp:useBean id="propertiesBean" type="jetbrains.buildServer.serverSide.oauth.OAuthConnectionBean" scope="request"/>
<bs:linkScript>
    /js/bs/testConnection.js
</bs:linkScript>
<script>
  BS.OAuthConnectionDialog.submitTestConnection = function () {
    var that = this;
    BS.PasswordFormSaver.save(this, '<c:url value="/admin/hashicorp-vault-test-connection.html"/>', OO.extend(BS.ErrorsAwareListener, {
      onFailedTestConnectionError: function (elem) {
        var text = "";
        if (elem.firstChild) {
          text = elem.firstChild.nodeValue;
        }
        BS.TestConnectionDialog.show(false, text, $('testConnectionButton'));
      },

      onCompleteSave: function (form, responseXML) {
        var err = BS.XMLResponse.processErrors(responseXML, this, form.propertiesErrorsHandler);
        BS.ErrorsAwareListener.onCompleteSave(form, responseXML, err);
        if (!err) {
          this.onSuccessfulSave(responseXML);
        }
      },

      onSuccessfulSave: function (responseXML) {
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
  BS.OAuthConnectionDialog.afterClose = function () {
    $j('#OAuthConnectionDialog .testConnectionButton').remove();
    afterClose()
  }
</script>
<tr>
    <td><label for="displayName">Display name:</label><l:star/></td>
    <td>
        <props:textProperty name="displayName" className="longField"/>
        <span class="smallNote">Provide some name to distinguish this connection from others.</span>
        <span class="error" id="error_displayName"></span>
    </td>
</tr>
<tr>
    <td><label for="${keys.URL}">Vault URL:</label></td>
    <td>
        <props:textProperty name="${keys.URL}"
                            className="longField textProperty_max-width js_max-width"/>
        <span class="error" id="error_${keys.URL}"/>
        <span class="smallNote">Specify Vault URL, like 'https://vault.service:8200/'</span>
    </td>
</tr>

<tr>
    <td><label for="${keys.ROLE_ID}">AppRole Role ID:</label></td>
    <td>
        <props:textProperty name="${keys.ROLE_ID}"
                            className="longField textProperty_max-width js_max-width"/>
        <span class="error" id="error_${keys.ROLE_ID}"/>
        <%--<span class="smallNote"></span>--%>
    </td>
</tr>

<tr class="noBorder">
    <td><label for="${keys.SECRET_ID}">AppRole Secret ID:</label></td>
    <td>
        <props:passwordProperty name="${keys.SECRET_ID}"
                                className="longField textProperty_max-width js_max-width"/>
        <span class="error" id="error_${keys.SECRET_ID}"/>
        <%--<span class="smallNote"></span>--%>
    </td>
</tr>

<forms:submit id="testConnectionButton" type="button" label="Test Connection" className="testConnectionButton"
              onclick="return BS.OAuthConnectionDialog.submitTestConnection();"/>
<bs:dialog dialogId="testConnectionDialog" title="Test Connection" closeCommand="BS.TestConnectionDialog.close();"
           closeAttrs="showdiscardchangesmessage='false'">
    <div id="testConnectionStatus"></div>
    <div id="testConnectionDetails" class="mono"></div>
</bs:dialog>
<script>
  $j("#testConnectionButton").appendTo($j('#OAuthConnectionDialog .popupSaveButtonsBlock')[0])
</script>