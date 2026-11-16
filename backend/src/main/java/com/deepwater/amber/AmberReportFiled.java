package com.deepwater.amber;

/** Published after an Amber member files a report. Only the reason travels; who reported whom stays in the database. */
public record AmberReportFiled(String reason) {}
