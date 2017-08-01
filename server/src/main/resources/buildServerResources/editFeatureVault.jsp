<%@ taglib prefix="props" uri="http://www.springframework.org/tags/form" %>
<%@ include file="/include-internal.jsp" %>
<jsp:useBean id="keys" class="org.jetbrains.teamcity.vault.server.VaultJspKeys"/>
<props:hidden name="teamcity.vault.requirement"/>
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

<tr class="noborder">
    <th><label for="${keys.VERIFY_SSL}">Verify SSL connection:</label></th>
    <td>
        <props:checkboxProperty name="${keys.VERIFY_SSL}"/>
        <span class="smallNote">When checked, Vault connection would verify SSL connection</span>
    </td>
</tr>