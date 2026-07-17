package org.nmox.studio.editor.polyglot;

import java.util.ArrayList;
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
import org.netbeans.spi.editor.completion.CompletionItem;
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
    @MimeRegistration(mimeType = "text/x-kotlin", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-csharp", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-fsharp", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-groovy", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-perl", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-r", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-julia", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-dart", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-scala", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-haskell", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-zig", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-gleam", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-nim", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-d", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-racket", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-elm", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-rescript", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-purescript", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-ocaml", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-crystal", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-vlang", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-fortran", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-smalltalk", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-prolog", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-tcl", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-scheme", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-ada", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-pascal", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-odin", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-cobol", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/x-solidity", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/coffeescript", service = CompletionProvider.class),
    // classic-library entries only (JS/HTML have their own primary providers)
    @MimeRegistration(mimeType = "text/javascript", service = CompletionProvider.class),
    @MimeRegistration(mimeType = "text/html", service = CompletionProvider.class)
})
public class PolyglotCompletionProvider implements CompletionProvider {

    private static final Pattern WORD = Pattern.compile("[A-Za-z_$][A-Za-z0-9_$]{1,}");

    /**
     * Mimes whose projects may use the classic web libraries; for these
     * the query merges catalog entries for whatever
     * {@link ClassicLibraryDetector} finds around the file. CoffeeScript
     * is here on purpose — the classic libraries were its era.
     */
    static final Set<String> CLASSIC_MIMES = Set.of(
            "text/javascript", "text/html", "text/coffeescript");

    private static final org.nmox.studio.editor.classic.ClassicLibraryDetector CLASSIC_DETECTOR =
            new org.nmox.studio.editor.classic.ClassicLibraryDetector();

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
                    + "mapOf setOf mutableListOf let also apply run with takeIf takeUnless lazy")),
            Map.entry("text/x-csharp", set("abstract as async await base bool break byte case catch char "
                    + "checked class const continue decimal default delegate do double else enum event explicit "
                    + "extern false finally fixed float for foreach goto if implicit in int interface internal "
                    + "is lock long namespace new null object operator out override params private protected "
                    + "public readonly record ref return sbyte sealed short sizeof stackalloc static string "
                    + "struct switch this throw true try typeof uint ulong unchecked unsafe ushort using var "
                    + "virtual void volatile while yield Task List Dictionary IEnumerable Console WriteLine")),
            Map.entry("text/x-fsharp", set("abstract and as assert base begin class default delegate do done "
                    + "downcast downto elif else end exception extern false finally for fun function if in "
                    + "inherit inline interface internal lazy let match member module mutable namespace new "
                    + "not null of open or override private public rec return select static struct then to "
                    + "true try type upcast use val void when while with yield async seq printfn")),
            Map.entry("text/x-groovy", set("abstract as assert boolean break byte case catch char class const "
                    + "continue def default do double else enum extends false final finally float for goto if "
                    + "implements import in instanceof int interface long native new null package private "
                    + "protected public return short static super switch this throw throws trait true try void "
                    + "volatile while it println each collect findAll closure")),
            Map.entry("text/x-perl", set("my our local sub if elsif else unless while until for foreach do "
                    + "last next redo return use no require package bless ref defined undef exists delete "
                    + "scalar wantarray print printf say die warn eval qw shift unshift push pop splice "
                    + "split join map grep sort reverse keys values each chomp chop lc uc strict warnings")),
            Map.entry("text/x-r", set("function if else repeat while for in next break TRUE FALSE NULL Inf NaN "
                    + "NA library require return invisible stop warning message print cat paste paste0 c list "
                    + "vector matrix data frame factor length names dim nrow ncol apply sapply lapply vapply "
                    + "mapply ggplot shiny renderPlot reactive observe")),
            Map.entry("text/x-julia", set("function macro module baremodule begin end if elseif else for while "
                    + "break continue return try catch finally let local global const struct mutable abstract "
                    + "primitive type quote do true false nothing missing using import export println push "
                    + "pop length map filter reduce broadcast")),
            Map.entry("text/x-dart", set("abstract as assert async await base break case catch class const "
                    + "continue covariant default deferred do dynamic else enum export extends extension "
                    + "external factory false final finally for get hide if implements import in interface is "
                    + "late library mixin new null on operator part required rethrow return sealed set show "
                    + "static super switch sync this throw true try typedef var void when while with yield "
                    + "String int double bool List Map Set Future Stream Widget BuildContext print")),
            Map.entry("text/x-scala", set("abstract case catch class def do else enum extends extension false "
                    + "final finally for given if implicit import lazy match new null object override package "
                    + "private protected return sealed super then throw trait true try type using val var "
                    + "while with yield Option Some None Either List Map Seq Future println")),
            Map.entry("text/x-haskell", set("case class data default deriving do else foreign if import in "
                    + "infix infixl infixr instance let module newtype of then type where qualified hiding as "
                    + "Maybe Just Nothing Either Left Right IO String Int Integer Bool True False return pure "
                    + "fmap mapM forM putStrLn print show read")),
            Map.entry("text/x-zig", set("addrspace align allowzero and anyframe anytype asm async await break "
                    + "callconv catch comptime const continue defer else enum errdefer error export extern fn "
                    + "for if inline noalias noinline nosuspend opaque or orelse packed pub resume return "
                    + "struct suspend switch test threadlocal try union unreachable usingnamespace var "
                    + "volatile while u8 u16 u32 u64 i32 i64 f32 f64 bool void type null undefined true false")),
            Map.entry("text/x-gleam", set("as assert auto case const echo else fn if implement import let "
                    + "opaque panic pub todo type use "
                    + "True False Nil Ok Error Int Float String Bool List Result")),
            Map.entry("text/x-nim", set("addr and as asm bind block break case cast concept const continue "
                    + "converter defer discard distinct div do elif else end enum except export finally for "
                    + "from func if import in include interface is isnot iterator let macro method mixin mod "
                    + "nil not notin object of or out proc ptr raise ref return shl shr static template try "
                    + "tuple type using var when while xor yield "
                    + "int int8 int16 int32 int64 uint float float32 float64 bool char string seq array "
                    + "openArray varargs set range true false echo len add newSeq")),
            Map.entry("text/x-d", set("abstract alias align asm assert auto body bool break byte case cast "
                    + "catch char class const continue dchar debug default delegate deprecated do double else "
                    + "enum export extern false final finally float for foreach foreach_reverse function goto "
                    + "if immutable import in inout int interface invariant is lazy long mixin module new "
                    + "nothrow null out override package pragma private protected public pure real ref return "
                    + "scope shared short static string struct super switch synchronized template this throw "
                    + "true try typeof ubyte uint ulong union unittest ushort version void wchar while with "
                    + "writeln writef import std")),
            Map.entry("text/x-racket", set("define lambda let let* letrec if cond case when unless begin "
                    + "set! quote quasiquote unquote and or not else require provide module module+ struct "
                    + "define-struct define-syntax syntax-rules match for for/list for/fold map filter foldl "
                    + "foldr apply values call/cc parameterize with-handlers displayln printf format list cons "
                    + "car cdr null empty first rest length reverse append lang racket racket/base")),
            Map.entry("text/x-elm", set("module exposing import as port type alias if then else case of "
                    + "let in Bool Int Float String Char List Maybe Just Nothing Result Ok Err Cmd Sub Html "
                    + "Program msg model init update view subscriptions main")),
            Map.entry("text/x-rescript", set("let rec and as assert constraint downto else exception external "
                    + "false for if in include lazy module mutable of open switch to true try type when while "
                    + "with async await bool int float string option array list unit None Some Ok Error "
                    + "Belt Js promise")),
            Map.entry("text/x-purescript", set("module where import as hiding data type newtype class instance "
                    + "derive if then else case of let in do ado forall infixl infixr foreign "
                    + "Boolean Int Number String Char Array Maybe Just Nothing Either Left Right Effect Aff Unit "
                    + "pure bind map discard")),
            Map.entry("text/x-vlang", set("as asm assert atomic break const continue defer else enum false fn "
                    + "for go goto if import in interface is isreftype lock match module mut none or "
                    + "return rlock select shared sizeof spawn static struct true type typeof union unsafe "
                    + "volatile pub bool string int i8 i16 i32 i64 u8 u16 u32 u64 f32 f64 rune byte voidptr "
                    + "any map array none error println print eprintln panic dump")),
            Map.entry("text/x-fortran", set("program end module subroutine function contains use implicit "
                    + "none integer real complex logical character double precision parameter dimension "
                    + "allocatable allocate deallocate pointer target intent in out inout optional "
                    + "if then else elseif endif do while cycle exit select case default where forall "
                    + "call return stop continue print write read open close format type class interface "
                    + "public private pure elemental recursive result associate block "
                    + "true false abstract extends procedure generic import kind len")),
            Map.entry("text/x-smalltalk", set("true false nil self super thisContext "
                    + "Object Transcript OrderedCollection Dictionary Set Array String Symbol "
                    + "ifTrue: ifFalse: whileTrue: whileFalse: do: collect: select: reject: detect: "
                    + "inject:into: new printNl displayNl printString value at:put: at: "
                    + "subclass: instanceVariableNames: classVariableNames: category:")),
            Map.entry("text/x-prolog", set("module use_module dynamic discontiguous multifile "
                    + "member append length reverse nth0 nth1 last msort sort findall bagof setof "
                    + "assert asserta assertz retract between succ_or_zero is mod rem abs "
                    + "atom number var nonvar atomic compound functor arg copy_term "
                    + "write writeln print read format halt true fail not catch throw")),
            Map.entry("text/x-tcl", set("proc set unset global variable upvar uplevel if elseif else "
                    + "switch while for foreach break continue return expr incr append lappend "
                    + "list lindex llength lrange lsort lsearch lmap dict array string split join "
                    + "regexp regsub format scan puts gets open close read eof file exec source "
                    + "namespace package eval catch error try finally after update vwait")),
            Map.entry("text/x-scheme", set("define lambda let let* letrec letrec* if cond case when unless "
                    + "begin do and or not else set! quote quasiquote unquote define-syntax syntax-rules "
                    + "define-record-type car cdr cons list append length reverse map for-each filter "
                    + "fold-left fold-right vector string number boolean pair null eq? eqv? equal? "
                    + "display newline write call/cc call-with-current-continuation values apply error")),
            Map.entry("text/x-ada", set("abort abs abstract accept access aliased all and array at begin "
                    + "body case constant declare delay delta digits do else elsif end entry exception "
                    + "exit for function generic goto if in interface is limited loop mod new not null "
                    + "of or others out overriding package pragma private procedure protected raise "
                    + "range record rem renames requeue return reverse select separate some subtype "
                    + "synchronized tagged task terminate then type until use when while with xor "
                    + "Integer Natural Positive Float Boolean Character String Put_Line Ada Text_IO")),
            Map.entry("text/x-pascal", set("program unit uses interface implementation begin end var const "
                    + "type procedure function record array of set file string integer real boolean char "
                    + "if then else case while do for to downto repeat until with nil not and or xor div "
                    + "mod in class object constructor destructor inherited property published private "
                    + "protected public virtual override overload writeln readln new dispose exit break "
                    + "continue try except finally raise")),
            Map.entry("text/x-odin", set("package import proc struct enum union bit_set map dynamic "
                    + "if else when switch case for in not_in defer return break continue fallthrough "
                    + "using transmute cast auto_cast distinct context or_else or_return where do "
                    + "true false nil int uint i8 i16 i32 i64 u8 u16 u32 u64 f16 f32 f64 rune string "
                    + "cstring rawptr bool byte matrix quaternion typeid any fmt println printf len cap")),
            Map.entry("text/x-cobol", set("IDENTIFICATION DIVISION PROGRAM-ID ENVIRONMENT DATA WORKING-STORAGE "
                    + "PROCEDURE SECTION PIC PICTURE VALUE MOVE TO ADD SUBTRACT MULTIPLY DIVIDE COMPUTE "
                    + "DISPLAY ACCEPT IF ELSE END-IF EVALUATE WHEN PERFORM UNTIL VARYING TIMES THRU "
                    + "CALL USING STOP RUN GOBACK OPEN CLOSE READ WRITE REWRITE FILE SELECT ASSIGN "
                    + "ORGANIZATION SEQUENTIAL INDEXED RELATIVE OCCURS REDEFINES FILLER SPACES ZEROS "
                    + "STRING UNSTRING INSPECT REPLACING INITIALIZE SET LEVEL COPY")),
            Map.entry("text/x-ocaml", set("and as assert asr begin class constraint do done downto else end "
                    + "exception external false for fun function functor if in include inherit initializer "
                    + "land lazy let lor lsl lsr lxor match method mod module mutable new nonrec object of "
                    + "open or private rec sig struct then to true try type val virtual when while with "
                    + "Some None Ok Error List Array String printf print_endline")),
            Map.entry("text/x-crystal", set("abstract alias annotation as asm begin break case class def do "
                    + "else elsif end ensure enum extend false for fun if in include instance_sizeof is_a "
                    + "lib macro module next nil of out pointerof private protected require rescue return "
                    + "select self sizeof struct super then true type typeof uninitialized union unless until "
                    + "verbatim when while with yield puts print pp getter setter property")),
            Map.entry("text/x-solidity", set("abstract address anonymous assembly bool break bytes calldata "
                    + "catch constant constructor continue contract delete do else emit enum error event "
                    + "external fallback for function if immutable import indexed interface internal is "
                    + "library mapping memory modifier new override payable pragma private public pure "
                    + "receive require return returns revert storage string struct this try type uint "
                    + "uint8 uint256 int int256 unchecked using view virtual while true false wei gwei "
                    + "ether keccak256 abi msg block tx assert selfdestruct")),
            Map.entry("text/coffeescript", set("if unless then else switch when for while until loop by "
                    + "class extends super this new return try catch finally throw break continue do "
                    + "yield await typeof instanceof delete in of not and or is isnt true false yes no "
                    + "on off null undefined require module exports -> =>")));

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

    /** Runs off the EDT: AsyncCompletionTask posts the query to a worker. */
    private static final class Query extends AsyncCompletionQuery {

        @Override
        protected void query(CompletionResultSet resultSet, Document doc, int caretOffset) {
            try {
                String mime = (String) doc.getProperty("mimeType");
                String text = doc.getText(0, doc.getLength());

                // keywords + buffer identifiers only for the mimes this
                // provider owns; on JS/HTML the primary providers already
                // offer both and doubling them would be noise
                if (KEYWORDS.containsKey(mime)) {
                    Set<String> keywords = KEYWORDS.get(mime);
                    String prefix = prefixAt(text, caretOffset);
                    int anchor = caretOffset - prefix.length();
                    for (String keyword : matchingKeywords(keywords, prefix)) {
                        resultSet.addItem(new JavaScriptKeywordCompletionItem(keyword, anchor, prefix.length()));
                    }
                    for (String word : matchingIdentifiers(text, keywords, prefix, caretOffset)) {
                        resultSet.addItem(new JavaScriptObjectCompletionItem(word, anchor, prefix.length()));
                    }
                }

                if (CLASSIC_MIMES.contains(mime)) {
                    java.nio.file.Path file = fileOf(doc);
                    if (file != null) {
                        for (CompletionItem item
                                : classicItems(CLASSIC_DETECTOR.detect(file), text, caretOffset)) {
                            resultSet.addItem(item);
                        }
                    }
                }
            } catch (BadLocationException ex) {
                // stale offsets; popup simply shows nothing
            } finally {
                resultSet.finish();
            }
        }
    }

    /** The edited file behind a document, or null (unsaved buffer, virtual fs). */
    private static java.nio.file.Path fileOf(Document doc) {
        org.openide.filesystems.FileObject fo =
                org.netbeans.modules.editor.NbEditorUtilities.getFileObject(doc);
        java.io.File file = fo == null ? null : org.openide.filesystems.FileUtil.toFile(fo);
        return file == null ? null : file.toPath();
    }

    /**
     * Completion items for every catalog entry of the detected libraries
     * that matches the classic prefix at the caret. Pure and
     * package-visible for tests: (libraries, text, caret) → items.
     */
    static List<CompletionItem> classicItems(Set<String> libraryIds, String text, int caretOffset) {
        if (libraryIds.isEmpty()) {
            return List.of();
        }
        String prefix = org.nmox.studio.editor.classic.ClassicApiMatcher.prefixAt(text, caretOffset);
        List<CompletionItem> out = new ArrayList<>();
        for (String id : libraryIds) {
            org.nmox.studio.editor.classic.ClassicApiCatalog.Library lib =
                    org.nmox.studio.editor.classic.ClassicApiCatalog.library(id);
            if (lib == null) {
                continue;
            }
            for (org.nmox.studio.editor.classic.ClassicApiCatalog.Entry entry : lib.entries()) {
                int len = org.nmox.studio.editor.classic.ClassicApiMatcher.matchLength(entry.name(), prefix);
                if (len >= 0) {
                    out.add(new org.nmox.studio.editor.classic.ClassicApiCompletionItem(
                            entry, lib.display(), caretOffset - len, len));
                }
            }
        }
        return out;
    }

    /**
     * The identifier fragment ending at {@code offset}: letters, digits,
     * underscore or {@code $} walked back from the caret. Pure and
     * package-visible for tests.
     */
    static String prefixAt(String text, int offset) {
        int start = Math.min(offset, text.length());
        int i = start;
        while (i > 0 && (Character.isLetterOrDigit(text.charAt(i - 1))
                || text.charAt(i - 1) == '_' || text.charAt(i - 1) == '$')) {
            i--;
        }
        return text.substring(i, start);
    }

    /** Language keywords matching the prefix (case-insensitive), sorted. */
    static List<String> matchingKeywords(Set<String> keywords, String prefix) {
        String prefixLower = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String keyword : new TreeSet<>(keywords)) {
            if (keyword.toLowerCase().startsWith(prefixLower)) {
                out.add(keyword);
            }
        }
        return out;
    }

    /**
     * Every identifier in the document that matches the prefix and isn't a
     * keyword — deduplicated and sorted — excluding the word the caret is
     * currently completing (the one ending exactly at {@code caretOffset}).
     */
    static List<String> matchingIdentifiers(String text, Set<String> keywords, String prefix, int caretOffset) {
        String prefixLower = prefix.toLowerCase();
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
        return new ArrayList<>(words);
    }
}
