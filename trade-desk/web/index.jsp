<%@ page language="java" contentType="text/html; charset=UTF-8" %>
<%@ page import="java.text.SimpleDateFormat" %>
<%@ page import="java.util.Date" %>
<%
    // Get current date for display
    SimpleDateFormat sdf = new SimpleDateFormat("EEEE, MMMM d, yyyy");
    String currentDate = sdf.format(new Date());
    SimpleDateFormat timeFmt = new SimpleDateFormat("h:mm a");
    String currentTime = timeFmt.format(new Date());
%>
<HTML>
<HEAD>
<TITLE>BigCorp Trading Desk v2.1</TITLE>
<STYLE>
  body { background-color: #C0C0C0; font-family: 'Times New Roman', Times, serif; margin: 0; }
  .header-bar { background-color: #000080; color: #FFFFFF; padding: 12px 15px; }
  .header-bar FONT { font-family: 'Times New Roman', Times, serif; }
  .content { padding: 20px; }
  .menu-table { border: 2px outset #808080; background-color: #D4D0C8; width: 400px; }
  .menu-table TD { padding: 8px 12px; font-size: 10pt; }
  .menu-table A { color: #000080; text-decoration: none; font-weight: bold; font-size: 11pt; }
  .menu-table A:hover { text-decoration: underline; }
  .menu-desc { color: #404040; font-size: 9pt; }
  .menu-icon { font-size: 14pt; width: 30px; text-align: center; }
  .welcome { font-size: 10pt; color: #333333; margin-bottom: 15px; }
  .footer { font-size: 8pt; color: #808080; text-align: center; padding: 10px; }
  .notice { background-color: #FFFFCC; border: 1px solid #CCCC00; padding: 8px; font-size: 9pt; width: 400px; margin-top: 15px; }
  HR { color: #808080; }
</STYLE>
</HEAD>
<BODY>

<!-- Header -->
<TABLE WIDTH="100%" CELLPADDING="0" CELLSPACING="0" BORDER="0">
<TR><TD CLASS="header-bar">
  <FONT SIZE="5"><B>BigCorp Trading Desk</B></FONT><BR>
  <FONT SIZE="2">Trade Order Management System v2.1</FONT>
</TD>
<TD CLASS="header-bar" ALIGN="right" VALIGN="top">
  <FONT SIZE="1">[LOGO]</FONT>
</TD>
</TR>
</TABLE>
<HR SIZE="2" NOSHADE>

<DIV CLASS="content">

<P CLASS="welcome">
  Welcome to the BigCorp Trading Desk.<BR>
  Today is <B><%= currentDate %></B> | Server time: <B><%= currentTime %></B>
</P>

<!-- Main menu -->
<TABLE CLASS="menu-table" CELLPADDING="0" CELLSPACING="0">
<TR>
  <TD COLSPAN="2" BGCOLOR="#000080">
    <FONT COLOR="#FFFFFF" SIZE="2"><B>&nbsp; Main Menu</B></FONT>
  </TD>
</TR>
<TR>
  <TD CLASS="menu-icon">&#9658;</TD>
  <TD>
    <A HREF="/trade-desk/order/entry">Submit New Order</A><BR>
    <SPAN CLASS="menu-desc">Enter a new trade order for processing</SPAN>
  </TD>
</TR>
<TR><TD COLSPAN="2"><HR SIZE="1"></TD></TR>
<TR>
  <TD CLASS="menu-icon">&#9658;</TD>
  <TD>
    <A HREF="/trade-desk/order/status">View Order Status</A><BR>
    <SPAN CLASS="menu-desc">Check status of existing orders</SPAN>
  </TD>
</TR>
</TABLE>

<!-- System notice -->
<DIV CLASS="notice">
  <B>System Notice:</B> The trading system will be unavailable for
  maintenance on Saturday, March 23rd from 2:00 AM to 6:00 AM EST.
  Please submit all orders before the maintenance window.
  <BR><FONT SIZE="1">- IT Operations (posted 2002-03-11)</FONT>
</DIV>

</DIV>

<HR SIZE="1" NOSHADE>
<DIV CLASS="footer">
  <FONT SIZE="1">
    BigCorp Trading Desk v2.1 | Internal Use Only<BR>
    &copy; 2002 BigCorp Financial Services, Inc. All rights reserved.<BR>
    Last updated: 2002-03-15 | Contact: helpdesk@bigcorp.com ext. 4357
  </FONT>
</DIV>

</BODY>
</HTML>
