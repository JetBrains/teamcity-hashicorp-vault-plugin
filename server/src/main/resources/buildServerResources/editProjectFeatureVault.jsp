<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include-internal.jsp" %>
<jsp:useBean id="publicKey" type="java.lang.String" scope="request"/>
<jsp:useBean id="currentProject" type="jetbrains.buildServer.serverSide.SProject" scope="request"/>
<jsp:useBean id="keys" class="org.jetbrains.teamcity.vault.server.VaultJspKeys"/>
<props:hiddenProperty name="teamcity.vault.requirement"/>
<bs:linkScript>
    /js/bs/testConnection.js
</bs:linkScript>
<script>
  BS.EditVaultFeatureForm = OO.extend(BS.PluginPropertiesForm, {
    formElement: function () {
      return $('vaultProjectFeatureForm')
    },
    submit: function (action) {
      $("submitVaultConnection").value = action;

      BS.PasswordFormSaver.save(this, this.formElement().action, OO.extend(BS.ErrorsAwareListener, {
        onCompleteSave: function (form, responseXML) {
          var err = BS.XMLResponse.processErrors(responseXML, this, form.propertiesErrorsHandler);
          BS.ErrorsAwareListener.onCompleteSave(form, responseXML, err);
          if (!err) {
            this.onSuccessfulSave(responseXML);
          }
        },
        onSuccessfulSave: function (responseXML) {
          BS.reload(true)
        }
      }));
      return false;
    },
    submitTestConnection: function () {
      $("submitVaultConnection").value = 'test-connection';
      var that = this;
      BS.PasswordFormSaver.save(this, this.formElement().action, OO.extend(BS.ErrorsAwareListener, {
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
    }
  })
</script>
<form id="vaultProjectFeatureForm" action="<c:url value="/admin/project/hashicorp-vault/edit.html"/>" method="post"
      onsubmit="return BS.EditVaultFeatureForm.submit('save')">
    <input type="hidden" id="publicKey" name="publicKey" value="${publicKey}">
    <input type="hidden" id="projectId" name="projectId" value="${currentProject.externalId}">
    <table class="runnerFormTable" style="width: 99%">
        <tr class="noBorder">
            <th><label for="${keys.URL}">Vault URL:</label></th>
            <td>
                <props:textProperty name="${keys.URL}"
                                    className="longField textProperty_max-width js_max-width"/>
                <span class="error" id="error_${keys.URL}"/>
                <span class="smallNote">Specify Vault URL, like 'https://vault.service:8200/'</span>
            </td>
        </tr>

        <tr class="noBorder">
            <th><label for="${keys.ROLE_ID}">AppRole Role ID:</label></th>
            <td>
                <props:textProperty name="${keys.ROLE_ID}"
                                    className="longField textProperty_max-width js_max-width"/>
                <span class="error" id="error_${keys.ROLE_ID}"/>
                <%--<span class="smallNote"></span>--%>
            </td>
        </tr>

        <tr class="noBorder">
            <th><label for="${keys.SECRET_ID}">AppRole Secret ID:</label></th>
            <td>
                <props:passwordProperty name="${keys.SECRET_ID}"
                                        className="longField textProperty_max-width js_max-width"/>
                <span class="error" id="error_${keys.SECRET_ID}"/>
                <%--<span class="smallNote"></span>--%>
            </td>
        </tr>
    </table>
    <div class="saveButtonsBlock">
        <input type="hidden" id="submitVaultConnection" name="do-action" value="save">
        <forms:submit name="submit-save" onclick="return BS.EditVaultFeatureForm.submit('save');" label="Save"/>
        <c:if test="${defined}">
            <forms:button id="submit-delete" onclick="return BS.EditVaultFeatureForm.submit('delete');" title="Delete">Delete</forms:button>
        </c:if>
        <forms:submit id="testConnectionButton" type="button" label="Test Connection"
                      onclick="return BS.EditVaultFeatureForm.submitTestConnection();"/>
        <forms:saving/>
    </div>
</form>

<bs:dialog dialogId="testConnectionDialog" title="Test Connection" closeCommand="BS.TestConnectionDialog.close();"
           closeAttrs="showdiscardchangesmessage='false'">
    <div id="testConnectionStatus"></div>
    <div id="testConnectionDetails" class="mono"></div>
</bs:dialog>
<forms:modified/>