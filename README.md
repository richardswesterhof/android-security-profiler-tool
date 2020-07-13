# Obtaining the program
1. First, clone or download this repository, it contains everything you need, from pre-built jars for ease of use, to source code for maximum customisability, and an example config file.

2. Next, make sure to have all tools installed. Before you do that, now is a good time to tell you that all tools are completely optional, if you want to omit one or more tools, we will cover in [Configuration](#configuration) how you can disable them and then you don't need to have them installed. The default tools that are supported are: [AmanDroid](https://github.com/arguslab/Argus-SAF), [AndroBugs](https://github.com/AndroBugs/AndroBugs_Framework), [AndroWarn](https://github.com/maaaaz/androwarn/), [FileScan](https://github.com/giovannipessiva/filescan), [FlowDroid](https://github.com/secure-software-engineering/FlowDroid), [Quark Engine](https://github.com/quark-engine/quark-engine), [RiskInDroid](https://github.com/ClaudiuGeorgiu/RiskInDroid), [SUPER](https://github.com/SUPERAndroidAnalyzer/super). Follow the instructions on their pages to install these tools.

3. Some tools don't work perfectly out of the box, so we will now go over some fixes. Now seems a good time to tell you again that all tools are optional to install. First we will modify FileScan (These instructions are based on commit c8c356e from January 12, 2013, for other commits, they may not be accurate): in file `test_filescan_class.py`, line 7 says: `from androguard.core.analysis.filescan import *`, this needs to be replaced by `from filescan import *`. Next on line 26 of the same file there is a hardcoded path, this needs to be replaced by `./`. Finally while we're here, it is important to note that a lot of automated requests to the online [malware hash registry](https://team-cymru.com/community-services/mhr/) are not allowed, so in file `filescan.py` line 68 says: `ENABLE_NET_CONNECTION = True`, this needs to be replaced by `ENABLE_NET_CONNECTION = False`. And that's it, we've successfully modified FileScan! In some instances, I got `ImportError: failed to find libmagic.  Check your installation`, if this happens run `pip uninstall python-magic` and then `pip install python-magic-bin==0.4.14`. 

4. Next, we'll make one quick to Quark Engine (These instructions are based on commit 33ced42 from April 24, 2020, for other commits, they may not be accurate). After downloading, but BEFORE installing the required packages, it is strongly recommended to set the required python version in the pipfile to the version you have, to prevent warnings showing up and potentially messing with the parsing of the output. That's all for this one.

5. RiskInDroid requires quite some changes, but it's risk score seems quite accurate, so it will be worth it (These instructions are based on commit ac2470b from April 24, 2020, for other commits, they may not be accurate). Most importantly, to interact with it from the command line, grab the custom main file called 'offline.py' from the releases on this repo. Place it in the RiskInDroid install folder and use this file to access RiskInDroid's analysis core (i.e. use `python3 offline.py [apk_path]`, at least during automated analysis). Next, I had to manually update `sqlalchemy` to the current latest (1.3.16), by `pip install -U sqlalchemy==1.3.16`. If `ValueError: Buffer dtype mismatch, expected 'SIZE_t' but got 'long long'` occurs, remove the contents of `app/models/`, make sure the zipped database at `app/database/` is extracted (try to extract it manually and overwrite the existing), and then submit one apk to the program. This will take a while, which is normal, and then the issue should be fixed. At last, you shall have a perfectly working version of RiskInDroid.

6. Finally, go into the `config/config.ini` file and make sure that all `execution_command`s listed will for the tools you have enabled work on your system. If not, make the necessary changes to them.


# Configuration
This tool supports customization through an ini file, located at `config/config.ini`. 
Here you can add new tools, disable tools you don't want to be included in the toolchain, and customize the commands that are used to execute each tool, among other things.
Every section for a tool starts with `[TOOL_NAME]`, where `TOOL_NAME` is the name of the tool, which the class representing the tool must be able to provide from its method `getIniSectionName()`.
In every section, there are a few mandatory keys. These keys include:
* `enabled`: determines whether or not the tool should be used.
* `execution_command`: the command that will be used to execute the tool.
* `execution_pwd`: the folder that the command will be executed in.

Furthermore, there are also the following non-mandatory keys:
* `save_stderr`: can be `true`, `false`, `on_error`, `not_empty`, or `if_useful`. Determines whether stdout will always, never, when exit code not zero, when it contains at least one character, or when exit code not zero OR it contains at least one character, will be saved, respectively.
* `continue_on_error`: determines whether the remaining `execution_command`s should still be executed if one of the ones before them exited with a non-zero exit code.
* `suppress_exit_code_warning_n`: determines whether a warning should be logged if the `execution_command` at (zero based) index `n` returned with a non-zero exit code.

Finally, it is also possible to add custom environment variables to the child processes that will run each `execution_command`. This can be done by adding a key `ENV_VAR_yourvarname = yourvarvalue` to your tool's section.

Apart from these keys, every section can reference any other keys from the enitre config using `${SECTION_NAME/KEY_NAME}`. Note that when using this, it is important to use the `Ini4j.Profile.Section.fetch` method instead of `Ini4j.Profile.Section.get` if you want to access these values in the java code, as the former properly resolves such references, whereas the latter gets the literal value. A tutorial of Ini4j can be found at this link: http://ini4j.sourceforge.net/tutorial/IniTutorial.java.html

There are also a few custom keywords which will be resolved to useful values you might need in the config file. These include:
* `@CURRENT_APK`:the complete path of the current apk that is being investigated.
* `@INPUT_DIR`: the complete path of the input directory given to the main program (i.e. the value of the `--input` parameter).
* `@OUTPUT_DIR`:the complete path of the output directory given to the main program (i.e. the value of the `--output` parameter).
* `@APK_NAME`: the name of the current apk file (including its extension).
* `@APK_PARENT_FILE`: the complete path of the folder in which the current apk lives.


# Running
Running the analyser:
```
usage: java -jar analyser.jar -i INPUT [options]
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
 -v,--version                          Print the version number and exit.
```
 
Running the parser:
```
usage: java -jar parser.jar -i INPUT [options]
 -c,--config <CONFIG_FILE>             The path to the config file to use.
                                       If left out, the default path will
                                       be used. (./config/config.ini)
 -h,--help                             Print this help message and exit.
 -i,--input <INPUT>                    The folder specified as the output
                                       path during the analysis stage. The
                                       parsed report will be stored in
                                       this folder as well, as
                                       'report.csv'. Default value is
                                       './apk_reports'.
 -l,--log-level <LOG_LEVEL>            Set the minimum level a message
                                       must be to be output to the log
                                       file. Can be one of {INFO, WARNING,
                                       ERROR, OFF}, default: ERROR.
 -lc,--log-level-console <LOG_LEVEL>   Set the minimum level a message
                                       must be to be output to stdout. Can
                                       be one of {INFO, WARNING, ERROR,
                                       OFF}, default: INFO.
 -s,--stacktrace                       If provided, print the stacktrace
                                       of any exceptions that occur.
 -v,--version                          Print the version number and exit.
```
Make sure that the input folder of the parser is the ouput folder of the analyser.


# Extension
Adding a new tool to the toolchain is simple. 
1. Install the new tool to your machine.
2. Know how you would run the tool manually from the command line.
3. Create a new section in the config file, using the mandatory and optional keys specified in [Configuration](#configuration). An example is available at the end of the provided config file as well.
4. Create a new Java class in `src/main/java/parsers` with the same name as your section in the config file, and make it extend `Parser`. Make sure to override all of the abstract methods this class has.
5. Run the toolchain and confirm that your tool is being used. A tip is to set log levels to INFO or more specific, as this will print a list of all tools that have been found. Since this program uses reflection and the name of the section in the config file, it will automatically include your new tool if you placed it in the correct directory and the name of the section matches the name of the class EXACTLY. However, if this fails for any reason, a last resort would be to add a new instance of your class manually to the tool list. To do this, open file `src/main/java/parsers/ParentParser.java`, then on line 62 there is an example of how to do this (`tools.add(new YourClass([params...]));`, and replace `[params...]` by the actual parameters for the constructor).
