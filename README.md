# Exposed DAO Code Generator
A command-line (for now) code generator for Kotlin [Exposed](https://github.com/JetBrains/Exposed).

It is designed to work with PostgreSQL.  It may work with other vendors (reports of working/not working are welcome).

If you want to try to adapt it to a different vendor,
look at the Type class and its uses, it is the only vendor dependant part.

Error reports / issues are welcome, but there is no guarantee I will get to it.
This isn't something I'm supporting, just something I use that might be useful.

# Usage

Download the JAR or build with gradle.

Run with the command arguments: `<connectionString> [-f outFile] [-s schema] [-nodao] [-cc] [-q]`
 * `connectionString` is the JDBC connection string.  **[MANDATORY]**
 * `outFile` file to output to.  Default is not to output the generated code to a file.  Optional, **must be preceded by -f**
 * `schema` schema to look at.  Default is all of them.  Optional, **must be preceded by -s**
 * `-nodao` means not to generate DAO (classes), just DSL (objects).  Optional.
 * `-cc` means to copy the generated code to the clipboard.  Optional.
 * `-q` means to run in quiet mode without outputting the generated code.  Optional.


## TODO / Does not support
 * Quoted names in Postgres
 * A UI
 * Better naming
 * Support for more ids / primary keys (dependent on Exposed [mostly])
 * via (how do I use this?  no Exposed docs)
