<%@ include file="/include-internal.jsp" %>
<jsp:useBean id="publicKey" type="java.lang.String" scope="request"/>
<jsp:useBean id="currentProject" type="jetbrains.buildServer.serverSide.SProject" scope="request"/>
<jsp:useBean id="keys" class="org.jetbrains.teamcity.vault.server.VaultJspKeys"/>
<props:hiddenProperty name="teamcity.vault.requirement"/>
<script>
  BS.EditVaultFeatureForm = OO.extend(BS.PluginPropertiesForm, {
    formElement: function () {
      return $('vaultProjectFeatureForm')
    },
    submit: function () {
      BS.FormSaver.save(this, this.formElement().action, OO.extend(BS.ErrorsAwareListener, {
        onCompleteSave: function (form, responseXML, err) {
          var wereErrors = BS.XMLResponse.processErrors(responseXML, BS.ErrorsAwareListener, form.propertiesErrorsHandler);
          BS.ErrorsAwareListener.onCompleteSave(form, responseXML, wereErrors);
          if (wereErrors) {
            BS.Util.reenableForm(form.formElement())
          } else {
            this.onSuccessfulSave(responseXML)
          }
        },

        onSuccessfulSave: function (responseXML) {
          BS.reload(true)
        }
      }));
      return false;
    }
  })
</script>
<form id="vaultProjectFeatureForm" action="<c:url value="/admin/project/hashicorp-vault/edit.html"/>" method="post"
      onsubmit="return BS.EditVaultFeatureForm.submit()">
    <input type="hidden" id="publicKey" name="publicKey" value="${publicKey}">
    <input type="hidden" id="projectId" name="projectId" value="${currentProject.externalId}">
    <table class="runnerFormTable" style="width: 99%">
        <tr class="noBorder">
            <th><label for="${keys.ENABLED}">Enabled</label></th>
            <td>
                <props:checkboxProperty name="${keys.ENABLED}"/>
                <span class="error" id="error_${keys.URL}"/>
                <%--<span class="smallNote"></span>--%>
            </td>
        </tr>

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
        <%--TODO: Add "Test Connection" button which would fetch wrapped token and revoke it using accessor --%>
    </table>
    <div class="saveButtonsBlock">
        <forms:submit name="submitButton" label="Save"/>
        <forms:saving/>
        <forms:modified/>
    </div>
</form>

