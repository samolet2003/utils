package logsplitter;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class LogSplitter {
    // 30-11-2017 16:01:22.095|DEBUG
    private static final String regexp = "^\\d{2}-\\d{2}-\\d{4} \\d{2}:\\d{2}:\\d{2}\\.\\d{3}( [A-Z0-9]+)?\\|.+";
    private List<File> files = new ArrayList<File>();
    private File outFile = new File("logsplitter.out");
    private List<String> includes = new ArrayList<String>();
    private List<String> excludes = new ArrayList<String>();
    private String charset = "UTF-8";
    private Writer writer;

    public LogSplitter() {
    }

    public static void main(String[] args) throws Exception {
        LogSplitter ls = new LogSplitter();
         ls.parseCommandLineParameters(args);
         ls.splitLogs();
    }

    private String beautifyData(String line) {
//        if (line.matches("[a-z]+[a-z0-9_]+=\\[")) {
            StringBuilder sb = new StringBuilder();
            int level = 0;
            for (int i = 0; i < line.length(); i++) {
                char c = line.charAt(i);
                if (c == '[') {
                    sb.append(c);
                    sb.append('\n');
                    level++;
                    for (int j = 0; j < level * 4; j++) {
                        sb.append(' ');
                    }
                } else if(c==']') {
                    sb.append(c);
                    sb.append('\n');
                    level--;
                    for (int j = 0; j < level * 4; j++) {
                        sb.append(' ');
                    }
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
//        }
//        return line;
    }

    public void parseCommandLineParameters(String[] args) {
        files = new ArrayList<File>();
        includes = new ArrayList<String>();
        excludes = new ArrayList<String>();
        charset = "UTF-8";
        for (String s : args) {
            if (s.startsWith("i=")) {
                includes.add(s.substring(2));
            } else if (s.startsWith("e=")) {
                excludes.add(s.substring(2));
            } else if (s.startsWith("c=")) {
                charset = s.substring(2);
            } else if (s.startsWith("out=")) {
                outFile = new File(s.substring(4));
            } else {
                File file = new File(s);
                files.add(file);
            }
        }
        if (files.isEmpty() || outFile == null || (includes.isEmpty() && excludes.isEmpty())) {
            System.err.println("Usage: LogSplitter i=include_string e=exclude_string c=charset out=outfile file");
            System.exit(-1);
        }
    }

    public void splitLogs() throws IOException {
        Pattern pattern = Pattern.compile(regexp);
        int i = 1;
        File origOutFile = outFile;
        while (outFile.exists()) {
            outFile = new File(origOutFile.getParentFile(), origOutFile.getName() + "." + i++);
        }
        System.out.println("outFile=" + outFile.getName());
        System.out.println("includes=" + includes);
        System.out.println("excludes=" + excludes);
        System.out.println("charset=" + charset);
        FileOutputStream fout = new FileOutputStream(outFile);
        writer = new OutputStreamWriter(fout, charset);
        for (File file : files) {
            System.out.println("file=" + file.getName());
            FileInputStream fin = new FileInputStream(file);
            BufferedReader reader = new BufferedReader(new InputStreamReader(fin, charset));
            String line;
            boolean include = false;
            while ((line = reader.readLine()) != null) {
                if (pattern.matcher(line).matches()) {
                    include = isIncludeLine(line);
//                    line = beautifyData(line);
                }
                if (include) {
                    writer.write(line);
                    writer.write('\n');
                }
            }
            reader.close();
            fin.close();
        }
        writer.close();
    }

    private boolean isIncludeLine(String line) {
        for (String inc : includes) {
            if (line.contains(inc))
                return true;
        }
        for (String exc : excludes) {
            if (line.contains(exc))
                return false;
        }
        if (includes.isEmpty() && !excludes.isEmpty())
            return true;
        if (!includes.isEmpty() && excludes.isEmpty())
            return false;

        return false;
    }
}
