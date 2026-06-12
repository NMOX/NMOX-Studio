package org.nmox.studio.editor.polyglot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.JTextComponent;
import org.netbeans.api.editor.mimelookup.MimeRegistration;
import org.netbeans.api.editor.mimelookup.MimeRegistrations;
import org.netbeans.spi.editor.completion.CompletionProvider;
import org.netbeans.spi.editor.completion.CompletionResultSet;
import org.netbeans.spi.editor.completion.CompletionTask;
import org.netbeans.spi.editor.completion.support.AsyncCompletionQuery;
import org.netbeans.spi.editor.completion.support.AsyncCompletionTask;
import org.nmox.studio.editor.completion.JavaScriptKeywordCompletionItem;
import org.nmox.studio.editor.completion.JavaScriptObjectCompletionItem;

/**
 * Completion for the polyglot languages: the language's keywords plus
 * every identifier already present in the document. Not semantic - but
 * fast, dependable, and exactly what a polyglot developer reaches for
 * when hopping between a Rust service and a Ruby script.
 */
@MimeRegistrations({
    @MimeRegistration(mimeType = "text/x-java", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-c", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-cpp", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-python", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-ruby", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-rust", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-php5", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/sh", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-go", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-erlang", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-elixir", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-clojure", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-lisp", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-lua", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-swift", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-kotlin", service = CompletionProvider.class)
})
public class PolyglotCompletionProvider implements CompletionProvider {

    private static final Pattern WORD = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{1,}");

    static final Map<String, Set<String>> KEYWORDS = Map.ofEntries(
            Map.entry("text/x-java", set("abstract assert boolean break byte case catch char class const continue "
                    + "default do double else enum extends final finally float for goto if implements import "
                    + "instanceof int interface long native new package private protected public record return "
                    + "sealed short static strictfp super switch synchronized this throw throws transient try "
                    + "var void volatile while yield true false null String List Map Set Optional Stream")),
            Map.entry("text/x-c", set("auto break case char const continue default do double else enum extern float for "
                    + "goto if inline int long register restrict return short signed sizeof static struct switch "
                    + "typedef union unsigned void volatile while size_t uint8_t uint16_t uint32_t uint64_t "
                    + "int8_t int16_t int32_t int64_t bool true false NULL printf malloc free")),
            Map.entry("text/x-cpp", set("alignas alignof auto bool break case catch char class concept const consteval "
                    + "constexpr constinit continue decltype default delete do double else enum explicit export "
                    + "extern false final float for friend goto if inline int long mutable namespace new noexcept "
                    + "nullptr operator override private protected public requires return short signed sizeof "
                    + "static struct switch template this throw true try typedef typename union unsigned using "
                    + "virtual void volatile while std string vector map unique_ptr shared_ptr")),
            Map.entry("text/x-python", set("False None True and as assert async await break class continue def del elif "
                    + "else except finally for from global if import in is lambda nonlocal not or pass raise "
                    + "return try while with yield self cls print len range enumerate zip dict list set tuple "
                    + "str int float bool isinstance super __init__ __name__ __main__")),
            Map.entry("text/x-ruby", set("BEGIN END alias and begin break case class def defined? do else elsif end ensure "
                    + "false for if in module next nil not or redo rescue retry return self super then true undef "
                    + "unless until when while yield require require_relative attr_accessor attr_reader "
                    + "attr_writer puts initialize new lambda proc each map select reject")),
            Map.entry("text/x-rust", set("as async await break const continue crate dyn else enum extern false fn for if "
                    + "impl in let loop match mod move mut pub ref return self Self static struct super trait "
                    + "true type unsafe use where while String Vec Option Some None Result Ok Err Box Rc Arc "
                    + "println eprintln vec format derive Debug Clone Copy")),
            Map.entry("text/x-php5", set("abstract and array as break callable case catch class clone const continue "
                    + "declare default do echo else elseif empty enddeclare endfor endforeach endif endswitch "
                    + "endwhile enum extends final finally fn for foreach function global goto if implements "
                    + "include instanceof insteadof interface isset list match namespace new or print private "
                    + "protected public readonly require return static switch throw trait try unset use var "
                    + "while xor yield true false null this self parent")),
            Map.entry("text/sh", set("if then else elif fi case esac for while until do done function return exit break "
                    + "continue local readonly export unset shift eval exec source alias echo printf read cd "
                    + "test true false set trap wait")),
            Map.entry("text/x-go", set("break case chan const continue default defer else fallthrough for func go goto if "
                    + "import interface map package range return select struct switch type var bool byte "
                    + "complex64 complex128 error float32 float64 int int8 int16 int32 int64 rune string uint "
                    + "uint8 uint16 uint32 uint64 uintptr true false nil iota append cap close copy delete len "
                    + "make new panic print println recover fmt Println Printf Errorf")),
            Map.entry("text/x-erlang", set("after begin case catch cond end fun if let of receive try when "
                    + "and andalso band bnot bor bsl bsr bxor div not or orelse rem xor "
                    + "module export import record define spec type behaviour gen_server gen_statem "
                    + "supervisor application start_link init handle_call handle_cast handle_info "
                    + "terminate code_change spawn spawn_link self exit error ok undefined true false "
                    + "lists maps proplists ets io format")),
            Map.entry("text/x-elixir", set("def defp defmodule defmacro defmacrop defstruct defprotocol "
                    + "defimpl defexception defguard defdelegate do end fn when case cond if unless else "
                    + "receive after rescue catch try raise throw import require alias use quote unquote "
                    + "with for true false nil and or not in __MODULE__ __DIR__ "
                    + "GenServer Supervisor Agent Task Enum Stream Map MapSet Keyword String Integer "
                    + "Float Process IO Kernel Application Registry send self spawn spawn_link "
                    + "start_link init handle_call handle_cast handle_info |> def_impl")),
            Map.entry("text/x-clojure", set("def defn defn- defmacro defmulti defmethod defprotocol "
                    + "defrecord deftype defonce let letfn fn if if-let if-not when when-let when-not "
                    + "cond condp case do loop recur for doseq dotimes while try catch finally throw "
                    + "ns require import use refer-clojure true false nil and or not "
                    + "map filter reduce apply partial comp juxt assoc dissoc conj cons first rest "
                    + "seq vec list set get-in update-in swap! reset! atom ref agent future promise "
                    + "println prn str keyword symbol namespace")),
            Map.entry("text/x-lisp", set("defun defmacro defvar defparameter defconstant defclass "
                    + "defmethod defgeneric defstruct defpackage lambda let let* flet labels "
                    + "if when unless cond case ecase typecase progn prog1 prog2 block return-from "
                    + "loop do do* dolist dotimes mapcar mapc reduce remove-if remove-if-not "
                    + "car cdr cons list append nth first rest setf setq push pop incf decf "
                    + "format print princ prin1 quote function funcall apply values multiple-value-bind "
                    + "in-package use-package require provide t nil eq eql equal equalp")),
            Map.entry("text/x-lua", set("and break do else elseif end false for function goto if in "
                    + "local nil not or repeat return then true until while "
                    + "print pairs ipairs next type tostring tonumber pcall xpcall error assert "
                    + "require table string math io os coroutine setmetatable getmetatable rawget rawset")),
            Map.entry("text/x-swift", set("actor any as associatedtype async await borrowing break case "
                    + "catch class consuming continue convenience default defer deinit didSet do dynamic "
                    + "else enum extension fallthrough false fileprivate final for func get guard if import "
                    + "in indirect infix init inout internal is lazy let mutating nil nonisolated nonmutating "
                    + "open operator optional override postfix precedencegroup prefix private protocol public "
                    + "repeat required rethrows return self set some static struct subscript super switch "
                    + "throw throws true try typealias unowned var weak where while willSet "
                    + "String Int Double Bool Array Dictionary Set Optional Result Task print")),
            Map.entry("text/x-kotlin", set("abstract actual annotation as break by catch class companion "
                    + "const constructor continue crossinline data do dynamic else enum expect external "
                    + "false final finally for fun get if import in infix init inline inner interface "
                    + "internal is lateinit noinline null object open operator out override package private "
                    + "protected public reified return sealed set super suspend tailrec this throw true try "
                    + "typealias val var vararg when where while "
                    + "String Int Long Double Boolean List MutableList Map MutableMap Set println listOf "
                    + "mapOf setOf mutableListOf let also apply run with takeIf takeUnless lazy")));

    private static Set<String> set(String words) {
        return Set.copyOf(Arrays.asList(words.split(" ")));
    }

    @Override
    public CompletionTask createTask(int queryType, JTextComponent component) {
        if (queryType != COMPLETION_QUERY_TYPE) {
            return null;
        }
        return new AsyncCompletionTask(new Query(), component);
    }

    @Override
    public int getAutoQueryTypes(JTextComponent component, String typedText) {
        return 0; // explicit Ctrl+Space only; auto-popup stays a JS/TS feature
    }

    private static final class Query extends AsyncCompletionQuery {

        @Override
        protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
            try {
                String mime = (String) doc.getProperty("mimeType");
                Set<String> keywords = KEYWORDS.getOrDefault(mime, Set.of());
                String text = doc.getText(0, doc.getLength());
                String prefix = prefixAt(text, caretOffset);
                int anchor = caretOffset - prefix.length();
                String prefixLower = prefix.toLowerCase();

                for (String keyword : new TreeSet<>(keywords)) {
                    if (keyword.toLowerCase().startsWith(prefixLower)) {
                        resultSet.addItem(new JavaScriptKeywordCompletionItem(keyword, anchor, prefix.length()));
                    }
                }
                Set<String> words = new TreeSet<>();
                Matcher m = WORD.matcher(text);
                while (m.find()) {
                    String word = m.group();
                    if (!keywords.contains(word)
                            && word.toLowerCase().startsWith(prefixLower)
                            && m.end() != caretOffset) {
                        words.add(word);
                    }
                }
                for (String word : words) {
                    resultSet.addItem(new JavaScriptObjectCompletionItem(word, anchor, prefix.length()));
                }
            } catch (BadLocationException ex) {
                // stale offsets; popup simply shows nothing
            } finally {
                resultSet.finish();
            }
        }

        private static String prefixAt(String text, int offset) {
            int start = Math.min(offset, text.length());
            int i = start;
            while (i > 0 && (Character.isLetterOrDigit(text.charAt(i - 1))
                    || text.charAt(i - 1) == '_' || text.charAt(i - 1) == '$')) {
                i--;
            }
            return text.substring(i, start);
        }
    }
}
