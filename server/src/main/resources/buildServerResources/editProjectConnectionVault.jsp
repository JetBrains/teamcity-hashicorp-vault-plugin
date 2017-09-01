<%@ taglib prefix="c" uri="http://java.sun.com/jsp/jstl/core" %>
<%@ include file="/include-internal.jsp" %>
<jsp:useBean id="keys" class="org.jetbrains.teamcity.vault.server.VaultJspKeys"/>

<jsp:useBean id="project" type="jetbrains.buildServer.serverSide.SProject" scope="request"/>
<jsp:useBean id="oauthConnectionBean" type="jetbrains.buildServer.serverSide.oauth.OAuthConnectionBean"
             scope="request"/>
<jsp:useBean id="propertiesBean" type="jetbrains.buildServer.serverSide.oauth.OAuthConnectionBean" scope="request"/>

<tr>
    <td><label for="displayName">Display name:</label><l:star/></td>
    <td>
        <props:textProperty name="displayName" className="longField"/>
        <span class="smallNote">Provide some name to distinguish this connection from others.</span>
        <span class="error" id="error_displayName"></span>
    </td>
</tr>
<tr>
    <th><label for="${keys.URL}">Vault URL:</label></th>
    <td>
        <props:textProperty name="${keys.URL}"
                            className="longField textProperty_max-width js_max-width"/>
        <span class="error" id="error_${keys.URL}"/>
        <span class="smallNote">Specify Vault URL, like 'https://vault.service:8200/'</span>
    </td>
</tr>

<tr>
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
<%--TODO: Add 'Test Connection' button--%>