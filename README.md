![Version 1.0.0](https://img.shields.io/badge/version-1.0.0-green.svg)
![Version 2.0.0-beta](https://img.shields.io/badge/beta-2.0.0-green.svg)

# Exposed DAO Code Generator

A code generator for Kotlin [Exposed](https://github.com/JetBrains/Exposed).

It is designed to work with PostgreSQL.  It may work with other vendors (reports of working/not working are welcome).

If you want to try to adapt it to a different vendor,
look at the Type class and its uses, it is the only vendor dependant part.

Error reports / issues are welcome, but there is no guarantee I will get to it.
This isn't something I'm supporting, just something I use that might be useful.

# Features

 * KotlinX Serializer generation (saved fields on serialization, loads from database on deserializiation)
 * Multiplatform support (with cross-platform capable serialization for easy data transfer)
 * Foreign and Referencing Keys
 * Optional assume nullable unless explcit `not null`
 * Imports and package statement
 * Export to class/package structure
 * `new` pesudo-Constructor that takes fields as arguments
 * Customizable names, mutability

# Limitations

 * Some data types
 * Anything Non-postgres
 * Must have one primary key that is an int or long type if you want DAO (DSL will still be generated)

# Usage

## GUI (ExposedDaoGenerator.jar)

Download the ExposedDaoGenerator.jar.  Run it.

Supports package names, save/load, import/export, and export to files (a file per table).

Also supports Kotlin multiplatform projects
(generates a common class, a JS class that will work on other platforms, and the JVM class with the exposed backend).

#####  UI is somewhat buggy, but as long as you don't try to break it you should be allright.
Issues and feedback are appreciated.

To start, hit File -> New or `CTRL-N` to create a new database from a connection string.

Use the left panel to select tables and columns/foreign keys.

You can change the names of the columns and keys, and make the class properties mutable.

**Some names will cause errors in the code**.  I plan to detect this, but its not in yet.

I plan to add the ability to change the type, and to exclude columns.

### Usage

To change settings, use `CTRL-SHIFT-O`.

You can save your (edited) database to a .daogen file using File -> Save or `CTRL-S`.

To change the save file, use File -> Save As or `CTRL-SHIFT-S`.

To open a saved file, use File -> Open or `CTRL-O`.

To export the generated code to a .kt file, use `CTRL-E`.

To change the export file, use `CTRL-SHIFT-E`.

To import the database from an exported file, use `CTRL-I`.

To export to files, use `CTRL-ALT-E`.

If Autosave is checked, the database will automatically be saved to the save file when any changes are made (if the save file is set).

If Auto Export is checked, the database will automatically be exported to the export file when any changes are made (if the export file is set).

They are both checked by default when the information is available.

## Command Line (daogen.jar)

Download daogen.jar or build with gradle.
The jar is /libs/daogen.jarif you build with gradle.


Run with the command arguments: `<connectionString> [-p package] [-f outFile] [-s schema] [-tables tablesCSVList] [-nodao] [-noserialize] [-multiplatform JVM/JS/Common] [-cc] [-q]`
 * `connectionString` is the JDBC connection string.  **[MANDATORY]**
 * `package` is the package to put in the package statement.  Optional, **must be preceded by -p**
 * `outFile` is the file to output to.  Default is not to output the generated code to a file.  Optional, **must be preceded by -f**
 * `schema` is schema to look at.  Default is all of them.  Optional, **must be preceded by -s**
 * `tablesCSVList` is the list of tables, separated by commas, to look at.  Default is all of them.  Optional, **must be preceded by -tables**
 * `-nodao` means not to generate DAO (classes), just DSL (objects).  Optional.
 * `-noserialize` means not to generate KotlinX Serializers.  Optional.
 * `-multiplatform` optionally generates the DAO for a multiplatform project's platform.
 `-multiplatform` can be followed by `JVM`, `JS`, or `Common`, which causes daogen to output for that platform.
 Note that specifying `JVM` here is different than no multiplatform at all; with `JVM`, `actual` statements will be included.
 * `-cc` means to copy the generated code to the clipboard.  Optional.
 * `-q` means to run in quiet mode without outputting the generated code.  Optional.


To see the version of the jar, use `--version`.


## TODO / Does not support
 * Quoted names in Postgres
 * Better naming
 * Support for more ids / primary keys (dependent on Exposed [mostly])
 * via
