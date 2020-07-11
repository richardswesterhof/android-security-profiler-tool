# android_security_profiler
Android security profiler tool. Bachelor project at RUG supervised by F.F.M. Mohsen 2020


# Configuration
This tool supports customization through an ini file, located at `tool/config/config.ini`. 
Here you can add new tools, disable tools you don't want to be included in the toolchain, and customize the commands that are used to execute each tool, among other things.
Every section for a tool starts with `[TOOL_NAME]`, where `TOOL_NAME` is the name of the tool, which the class representing the tool must be able to provide from its method `getIniSectionName()`.
In every section, there are a few mandatory keys. These keys include:
* `enabled`: determines whether or not the tool should be used.
* `execution_command`: the command that will be used to execute the tool.
* `execution_pwd`: the folder that the command will be executed in.

Furthermore, there are also the following non-mandatory keys:
* `save_stdout`: can be `true`, `false`, or `on_error`. Determines whether stdout will always, never or only when an error occured be saved.
* `save_stderr`: can be `true`, `false`, or `on_error`. Determines whether stdout will always, never or only when an error occured be saved.
* `continue_on_error`: determines whether the remaining`execution_command`s should still be executed if one of the ones before them exited with an error.
* `suppress_exit_code_warning_n`: determines whether a warning should be logged if the `execution_command` at index `n` returned with a non-zero exit code.

Finally, it is also possible to add custom environment variables to the processes that will run each `execution_command`. This can be done by adding a key `ENV_VAR_yourvarname = yourvarvalue` to your tool's section.

Apart from these keys, every section can reference any other keys from the enitre config using `${SECTION_NAME/KEY_NAME}`. Note that when using this, it is important to use the `fetch` method instead of `get`.

There are also a few custom keywords which can be resolved to useful values you might need in a key value. These include:
* `@CURRENT_APK`:the complete path of the current apk that is being investigated.
* `@INPUT_DIR`: the complete path of the specified input directory
* `@OUTPUT_DIR`:the complete path of the specified output directory
* `@APK_NAME`: the name of the current apk file (including its extension)
* `@APK_PARENT_FILE`: the complete path of the folder in which the current apk lives.


# Running

`usage: java -jar tool.jar -i INPUT [options]
 -b,--bulk                             If this argument is provided, the
                                       INPUT argument should be a
                                       directory containing all the APKs
                                       to be analysed.
 -c,--config <CONFIG_FILE>             The path to the config file to use.
                                       If left out, the default path will
                                       be used.
 -h,--help                             Print this help message and exit.
 -i,--input <INPUT>                    Either the directory containing all
                                       APKs to be analysed in bulk mode,
                                       or the APK file to be analysed in
                                       single mode.
 -ir,--individual-reports              If provided, the reports will be
                                       stored per apk instead of in one
                                       large file. Useful for preventing
                                       the filesize from becoming too big,
                                       or for preventing the loss of all
                                       results generated before a fatal
                                       crash.
 -l,--log-level <LOG_LEVEL>            Set the minimum level a message
                                       must be to be output to the log
                                       file. Can be one of {INFO, WARNING,
                                       ERROR, OFF}, default: ERROR.
 -lc,--log-level-console <LOG_LEVEL>   Set the minimum level a message
                                       must be to be output to stdout. Can
                                       be one of {INFO, WARNING, ERROR,
                                       OFF}, default: INFO.
 -m,--max-apks <arg>                   The maximum amount of apks to
                                       analyze.
 -o,--output <OUTPUT>                  The directory to store the output
                                       to. If left out, it will be in a
                                       subdirectory called 'apk_reports'
                                       relative to your current directory.
 -r,--recursive                        Goes through all subdirectories of
                                       the provided INPUT directory. Only
                                       works in bulk mode.
 -s,--stacktrace                       If provided, print the stacktrace
                                       of any exceptions that occur.
 -t,--time                             Set a stopwatch for the
                                       investigation of each apk and the
                                       entire program.
 -v,--version                          Print the version number and exit.`


# Extension
Adding a new tool to the toolchain is simple. 
1. Install the new tool to your machine.
2. Know how you would run the tool manually from the command line.
3. Create a new section in the config file, using the mandatory and optional keys specified in [Configuration](#configuration).
4. Create a new Java class in `src/main/java/analyzers` with the same name as your section in the config file, and make it extend `Investigator`. Make sure to override all of the abstract methods this class has.
5. Run the toolchain and confirm that your tool is being used. A tip is to set log levels to INFO or more specific, as this will print a list of all tools that have been found. Since this program uses reflection and the name of the section in the config file, it will automatically include your new tool if you placed it in the correct directory and the name of the section matches the name of the class EXACTLY. However, if this fails for any reason, a last resort would be to add a new instance of your class 
