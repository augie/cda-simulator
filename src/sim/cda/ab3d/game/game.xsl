<xsl:stylesheet version = '1.0'
     xmlns:xsl='http://www.w3.org/1999/XSL/Transform'>
    <xsl:output method = "html" /> 
    <xsl:template match="gameID">
        <H1>
            Game 
            <xsl:value-of select="."/>
        </H1>
    </xsl:template>
</xsl:stylesheet> 