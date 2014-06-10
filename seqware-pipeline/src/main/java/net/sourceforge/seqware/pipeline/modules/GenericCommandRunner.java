package net.sourceforge.seqware.pipeline.modules;

import java.io.File;
import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import joptsimple.OptionException;
import joptsimple.OptionParser;
import joptsimple.OptionSet;
import net.sourceforge.seqware.common.model.ProcessingAttribute;
import net.sourceforge.seqware.common.module.FileMetadata;
import net.sourceforge.seqware.common.module.ReturnValue;
import net.sourceforge.seqware.common.util.Log;
import net.sourceforge.seqware.common.util.filetools.FileTools;
import net.sourceforge.seqware.common.util.runtools.RunTools;
import net.sourceforge.seqware.pipeline.module.Module;
import net.sourceforge.seqware.pipeline.module.ModuleInterface;

import org.openide.util.lookup.ServiceProvider;

/**
 * This is a very simple module that takes a few options on the command line and
 * then passes the rest of the arguments to whatever command is specified.
 * Generally, this shouldn't be used in production workflows, you should write
 * modules specific to the analysis/tool being wrapped by the module. However
 * this module is extremely helpful for prototyping complex workflows since it
 * can be used to quickly wrap simple calls to command line tools.
 *
 * Here's an example of how you might call the program:
 *
 * ./bin/seqware-runner.sh --no-metadata --module
 * net.sourceforge.seqware.pipeline.modules.GenericCommandRunner --
 * --gcr-output-file test:test:/tmp/foo.txt --gcr-command /bin/echo foobar >
 * /tmp/foo.txt
 *
 * You could, of course, supply metadata db information so the status is written
 * to the database and also a parent accession along with an accession output
 * file to be read in by a subsequent step.
 *
 * Please use JavaDoc to document each method (the user interface documents will
 * be autogenerated using these comments). See
 * http://en.wikipedia.org/wiki/Javadoc for more information.
 *
 * @author briandoconnor@gmail.com
 * @version $Id: $Id
 */
@ServiceProvider(service = ModuleInterface.class)
public class GenericCommandRunner extends Module {
    public static final String GCRSTDOUT = "gcr-stdout";
    public static final String GCRSTDERR = "gcr-stderr";
    public static final String GCRSTDERRBUFFERSIZE = "gcr-stderr-buffer-size";
    public static final String GCRSTDOUTBUFFERSIZE = "gcr-stdout-buffer-size";

    private OptionSet options = null;
    private File tempDir = null;
    private ArrayList<String> cmdParameters = null;
    public static final int DEFAULT_QUEUE_LENGTH = 10;
    private int stdoutQueueLength = Integer.MAX_VALUE;
    private int stderrQueueLength = Integer.MAX_VALUE;

    /**
     * getOptionParser is an internal method to parse command line args.
     *
     * @return OptionParser this is used to get command line options
     */
    @Override
    protected OptionParser getOptionParser() {
        OptionParser parser = new OptionParser();

        // FIXME: did I setup a boolean correctly!?!?

        parser.accepts(
                "gcr-output-file",
                "Specify this option one or more times for each output file created by the command called by this module. The argument is a '::' delimited list of type, meta_type, and file_path.").withRequiredArg().ofType(String.class).describedAs("Optional: <type:meta_type:file_path>");
        parser.accepts("gcr-command", "The command being executed (quote as needed).").withRequiredArg().ofType(String.class).describedAs("Required");
        parser.accepts(
                "gcr-algorithm",
                "You can pass in an algorithm name that will be recorded in the metadb if you are writing back to the metadb, otherwise GenericCommandRunner is used.").withRequiredArg().ofType(String.class).describedAs("Optional");
        parser.accepts(
                "gcr-skip-if-output-exists",
                "If the registered output files exist then this step won't be run again. This only works if gcr-output-file is defined too since we need to be able to check the output files to see if they exist. If this step produces no output files then it's hard to say if it was run successfully before.");
        parser.accepts(
                "gcr-skip-if-missing",
                "If the registered output files don't exist don't worry about it. Useful for workflows that can produce variable file outputs but also potentially dangerous.");
        parser.accepts("gcr-check-output-file", "Specify the path to the file.").withRequiredArg();
        parser.accepts(GCRSTDOUTBUFFERSIZE, "Used if "+ GCRSTDOUT + " is not set. This sets the number of lines of stdout to report (Default is "+DEFAULT_QUEUE_LENGTH+"). ").withRequiredArg().ofType(Integer.class).describedAs("Optional").defaultsTo(DEFAULT_QUEUE_LENGTH);
        parser.accepts(GCRSTDERRBUFFERSIZE, "Used if "+ GCRSTDERR+ " is not set. This sets the number of lines of stderr to report (Default is "+DEFAULT_QUEUE_LENGTH+"). ").withRequiredArg().ofType(Integer.class).describedAs("Optional").defaultsTo(DEFAULT_QUEUE_LENGTH);
        // SEQWARE-1668 GCR needs the ability to output to stdout and stderr for debugging
        parser.accepts(GCRSTDOUT, "Optional: Reports the full stdout (stdout of the command called is normally trimmed to "+GCRSTDOUTBUFFERSIZE+" lines");
        parser.accepts(GCRSTDERR, "Optional: Returns the full stderr (stderr of the command called is normally trimmed to "+GCRSTDERRBUFFERSIZE+" lines");
        return (parser);
    }


    /**
     * {@inheritDoc}
     *
     * A method used to return the syntax for this module
     * @return 
     */
    @Override
    public String get_syntax() {
        OptionParser parser = getOptionParser();
        StringWriter output = new StringWriter();
        try {
            parser.printHelpOn(output);
            return (output.toString());
        } catch (IOException e) {
            return (e.getMessage());
        }
    }

    /**
     * {@inheritDoc}
     *
     * The init method is where you put any code needed to setup your module.
     * Here I set some basic information in the ReturnValue object which will
     * eventually populate the "processing" table in seqware_meta_db. I also
     * create a temporary directory using the FileTools object.
     *
     * init is optional
     * @return 
     */
    @Override
    public ReturnValue init() {

        // setup the return value object, notice that we use
        // ExitStatus, this is what SeqWare uses to track the status
        ReturnValue ret = new ReturnValue();
        ret.setExitStatus(ReturnValue.SUCCESS);
        // fill in the algorithm field in the processing table
        ret.setAlgorithm("GenericCommandRunner");
        // fill in the description field in the processing table
        ret.setDescription("This is a simple command runner.");
        // fill in the version field in the processing table
        ret.setVersion("0.7.0");

        try {

            OptionParser parser = getOptionParser();

            // The parameters object is actually an ArrayList of Strings created
            // by splitting the command line options by space. JOpt expects a String[]

            // an array for this module
            ArrayList<String> myParameters = new ArrayList<>();

            // an array for everything else that will get passed to the command
            cmdParameters = new ArrayList<>();

            // should be able to do this since all the --gcr-* params take an argument
            for (int i = 0; i < this.getParameters().size(); i++) {
                final String param = this.getParameters().get(i);
                if (param.startsWith("--gcr-")) {
                    // except these ones!
                    if (param.startsWith("--gcr-skip-if") || param.equals("--" + GenericCommandRunner.GCRSTDERR) || param.equals("--" + GenericCommandRunner.GCRSTDOUT)) {
                        myParameters.add(param);
                    } else {
                        myParameters.add(param);
                        myParameters.add(this.getParameters().get(i + 1));
                        i++;
                    }
                } else {
                    cmdParameters.add(param);
                }
            }

            options = parser.parse(myParameters.toArray(new String[0]));

            stdoutQueueLength = Integer.valueOf(options.valueOf(GCRSTDOUTBUFFERSIZE).toString());
            stderrQueueLength = Integer.valueOf(options.valueOf(GCRSTDERRBUFFERSIZE).toString());
            if (options.has(GCRSTDOUT)) {
                stdoutQueueLength = Integer.MAX_VALUE;
            }
            if (options.has(GCRSTDERR)) {
                stderrQueueLength = Integer.MAX_VALUE;
            }
            
            // if algo is defined save the new value
            if (options.has("gcr-algorithm")) {
                ret.setAlgorithm((String) options.valueOf("gcr-algorithm"));
            }

            // create a temp directory in current working directory
            tempDir = FileTools.createTempDirectory(new File("."));

            // you can write to "stdout" or "stderr" which will be persisted back to
            // the DB
            // SEQWARE-1669
            // since nothing has been run yet ret.getStdout() is always null, "output-file" doesn't exist 
            // this this always output "nullOutput: null"
            // ret.setStdout(ret.getStdout() + "Output: " + (String) options.valueOf("output-file") + "\n");

        } catch (OptionException e) {
            ret.setStderr(e.getMessage());
            ret.setExitStatus(ReturnValue.INVALIDPARAMETERS);
        } catch (IOException e) {
            ret.setStderr(e.getMessage());
            ret.setExitStatus(ReturnValue.DIRECTORYNOTWRITABLE);
        }

        // now return the ReturnValue
        return (ret);

    }

    /**
     * {@inheritDoc}
     *
     * Verifies that the parameters make sense
     * @return 
     */
    @Override
    public ReturnValue do_verify_parameters() {

        // most methods return a ReturnValue object
        ReturnValue ret = new ReturnValue();
        ret.setExitStatus(ReturnValue.SUCCESS);

        // now look at the options and make sure they make sense
        if (!options.has("gcr-command") && !options.has("gcr-script")){
          ret.setExitStatus(ReturnValue.INVALIDPARAMETERS);
          ret.setStderr("Missing required parameter: --gcr-command");
        }

        return ret;
    }

    /**
     * {@inheritDoc}
     *
     * The do_verify_input method ensures that the input is reasonable and valid
     * for this tool. For this generic command runner we really can't tell if
     * the
     * @return 
     */
    @Override
    public ReturnValue do_verify_input() {

        // not much to do, let's make sure the
        // temp directory is writable
        ReturnValue ret = new ReturnValue();
        ret.setExitStatus(ReturnValue.SUCCESS);

        // Notice the FileTools actually returns ReturnValue objects too!
        if (FileTools.dirPathExistsAndWritable(tempDir).getExitStatus() != ReturnValue.SUCCESS) {
            ret.setExitStatus(ReturnValue.DIRECTORYNOTWRITABLE);
            ret.setStderr("Can't write to temp directory");
        }

        return (ret);

    }

    /**
     * {@inheritDoc}
     *
     * This is really an optional method but a very good idea. You would test
     * the programs your calling here by running them on a "known good" test
     * dataset and then compare the new answer with the previous known good
     * answer. Other forms of testing could be encapsulated here as well.
     * @return 
     */
    @Override
    public ReturnValue do_test() {

        // notice the use of "NOTIMPLEMENTED", this signifies that we simply
        // aren't doing this step. It's better than just saying SUCCESS
        ReturnValue ret = new ReturnValue();
        ret.setExitStatus(ReturnValue.NOTIMPLEMENTED);

        // not much to do, just return
        return (ret);
    }

    /**
     * {@inheritDoc}
     *
     * This is the core of a module. While some modules may be written in pure
     * Java or use various third-party Java APIs, the vast majority of modules
     * will use this method to make calls out to the shell (typically the BASH
     * shell in Linux) and use that shell to execute various commands. In an
     * ideal world this would never happen, we would all write out code with a
     * language-agnostic, network-aware API (e.g. thrift, SOAP, etc). But until
     * that day comes most programs in bioinformatics are command line tools (or
     * websites). So the heart of the module is it acts as a way for us to treat
     * the disparate tools as well-behaved modules that present a standard
     * interface and report back their metadata in well-defined ways. That's,
     * ultimately, what this object and, in particular this method, are all
     * about.
     *
     * There are other alternatives out there, such as Galaxy, that may provide
     * an XML syntax for accomplishing much of the same thing. For example, they
     * make disparate tools appear to function the same because the
     * inputs/outputs are all described using a standardized language. We chose
     * Java because it was more expressive than XML as a module running
     * descriptor. But clearly there are a lot of ways to solve this problem.
     * The key concern, though, is that a module should present very clear
     * inputs and outputs based, whenever possible, on standardized file types.
     * This makes it easy to use modules in novel workflows, rearranging them as
     * needed. Make every effort to make your modules self-contained and robust!
     * @return 
     */
    @Override
    public ReturnValue do_run() {

        // prepare the return value
        ReturnValue ret = new ReturnValue();
        ret.setExitStatus(ReturnValue.SUCCESS);

        // record the file output
        if (options.has("gcr-output-file")) {
            List<String> files = (List<String>) options.valuesOf("gcr-output-file");
            for (String file : files) {
                FileMetadata fm = new FileMetadata();
                String[] tokens = file.split("::");
                fm.setType(tokens[0]);
                fm.setMetaType(tokens[1]);
                fm.setFilePath(tokens[2]);
                fm.setDescription("A file output from the GenericCommandRunner which executed the command \""
                    + (String) options.valueOf("gcr-command") + "\".");
                ret.getFiles().add(fm);
                // handler the key-value
                if (fm.getMetaType().equals("text/key-value") && this.getProcessingAccession() != 0) {
                    Map<String, String> map = FileTools.getKeyValueFromFile(fm.getFilePath());
                    Set<ProcessingAttribute> atts = new TreeSet<>();
                    for (Map.Entry<String, String> entry : map.entrySet()) {
                        ProcessingAttribute a = new ProcessingAttribute();
                        a.setTag(entry.getKey());
                        a.setValue(entry.getValue());
                        atts.add(a);
                    }
                    this.getMetadata().annotateProcessing(this.getProcessingAccession(), atts);
                }
            }
        }

        // only an option if there are output files registered that can be checked
        if (options.has("gcr-skip-if-output-exists") && options.has("gcr-output-file")) {
            ReturnValue outputRetVal = do_verify_output();
            if (outputRetVal.getExitStatus() == ReturnValue.SUCCESS) {
                // if these are true just exit here
                return (ret);
            }
        }

        // track the start time of do_run for timing purposes
        ret.setRunStartTstmp(new Date());

        // save StdErr and StdOut
        StringBuilder stderr = new StringBuilder();
        StringBuilder stdout = new StringBuilder();

        ArrayList<String> theCommand = new ArrayList<>();
        theCommand.add("bash");
        theCommand.add("-lc");
        StringBuilder cmdBuff = new StringBuilder();
        cmdBuff.append((String) options.valueOf("gcr-command")).append(" ");
        for (String token : cmdParameters) {
            cmdBuff.append(token).append(" ");
        }
        theCommand.add(cmdBuff.toString());
        Log.stdout("Command run: \nbash -lc " + cmdBuff.toString());
        ReturnValue result = RunTools.runCommand(null, theCommand.toArray(new String[0]), stdoutQueueLength, stderrQueueLength);
        Log.stdout("Command exit code: " + result.getExitStatus());
        // ReturnValue result = RunTools.runCommand(new String[] { "bash", "-c",
        // (String)options.valueOf("gcr-command"), cmdParameters.toArray(new
        // String[0])} );
        stderr.append(result.getStderr());
        stdout.append(result.getStdout());

        ret.setStdout(stdout.toString());
        ret.setStderr(stderr.toString());
            
        
        if (result.getProcessExitStatus() != ReturnValue.SUCCESS || result.getExitStatus() != ReturnValue.SUCCESS) {
            ret.setExitStatus(result.getExitStatus());
            ret.setProcessExitStatus(result.getProcessExitStatus());
            ret.setStderr(stderr.toString());
            ret.setStdout(stdout.toString());
            return (ret);
        }

        // note the time do_run finishes
        ret.setRunStopTstmp(new Date());
        
        if (options.has("gcr-check-output-file")) {
        	ret = this.checkOutputFile();
        }
        return (ret);

    }

    /**
     * {@inheritDoc}
     *
     * A method to check to make sure the output was created correctly
     * @return 
     */
    @Override
    public ReturnValue do_verify_output() {

        ReturnValue ret = new ReturnValue();
        ret.setExitStatus(ReturnValue.SUCCESS);

        if (options.has("gcr-output-file") && !options.has("gcr-skip-if-missing")) {
            List<String> files = (List<String>) options.valuesOf("gcr-output-file");
            for (String file : files) {
                String[] tokens = file.split("::");
                if (FileTools.fileExistsAndReadable(new File(tokens[2])).getExitStatus() != ReturnValue.SUCCESS) {
                    Log.error("File does not exist or is not readable: " + tokens[2]);
                    ret.setExitStatus(ReturnValue.FILENOTREADABLE);
                    return (ret);
                }
                /*
                 * else if (FileTools.fileExistsAndNotEmpty(new
                 * File(tokens[2])).getExitStatus() != ReturnValue.SUCCESS) {
                 * ret.setExitStatus(ReturnValue.FILEEMPTY); return(ret); }
                 */
            }
        }

        return (ret);

    }

    /**
     * {@inheritDoc}
     *
     * A cleanup method, make sure you cleanup files that are outside the
     * current working directory since Pegasus won't clean those for you.
     *
     * clean_up is optional
     * @return 
     */
    @Override
    public ReturnValue clean_up() {

        ReturnValue ret = new ReturnValue();
        ret.setExitStatus(ReturnValue.SUCCESS);

        if (!tempDir.delete()) {
            ret.setExitStatus(ReturnValue.DIRECTORYNOTWRITABLE);
            ret.setStderr("Can't delete folder: " + tempDir.getAbsolutePath());
        }

        return (ret);
    }
    
    private ReturnValue checkOutputFile() {
    	ReturnValue newret = new ReturnValue(ReturnValue.SUCCESS);
    	 List<String> files = (List<String>) options.valuesOf("gcr-check-output-file");
         for (String file : files) {
             if (FileTools.fileExistsAndNotEmpty(new File(file)).getExitStatus() != ReturnValue.SUCCESS) {
                 Log.error("File does not exist or is not readable: " + file);
                 newret.setExitStatus(ReturnValue.FILENOTREADABLE);
                 return (newret);
             }
         }
         return newret;
    }
}
