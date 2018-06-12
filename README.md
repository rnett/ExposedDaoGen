# Exposed DAO Code Generator
Quick and dirty Kotlin Exposed DAO code generator.  Emphasis on quick and dirty.

A script I'm using to generate DAO code, thought it could be usefull.  It probably won't work right away (it only supports the data I needed), but should be fairly easy to adapt.

Runs on create table statements from postgreSQL.  Can take input from a file argument or stdin.

Automatically copies output to clipboard.

#### Todo (maybe, someday, don't count on it):

1. camelCase names.  Not sure if possible.

2. Import directly from database.

3. Support more types

4. Support for non-int primary keys and non-int clustered primary keys (waiting on Exposed clustered key support).
