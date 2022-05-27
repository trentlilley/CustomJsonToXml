package org.json;

/*
Copyright (c) 2015 JSON.org

Permission is hereby granted, free of charge, to any person obtaining a copy
of this software and associated documentation files (the "Software"), to deal
in the Software without restriction, including without limitation the rights
to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
copies of the Software, and to permit persons to whom the Software is
furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all
copies or substantial portions of the Software.

The Software shall be used for Good, not Evil.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
*/


import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.function.Function;
import java.util.stream.Collectors;


/**
 * This provides static methods to convert an XML text into a JSONObject, and to
 * covert a JSONObject into an XML text.
 *
 * @author JSON.org
 * @version 2016-08-10
 */
@SuppressWarnings("boxing")
public class XML {
    public static final String ANSI_RED = "\u001B[31m";
    public static final String ANSI_RESET = "\u001B[0m";

    /** The Character '&amp;'. */
    public static final Character AMP = '&';

    /** The Character '''. */
    public static final Character APOS = '\'';

    /** The Character '!'. */
    public static final Character BANG = '!';

    /** The Character '='. */
    public static final Character EQ = '=';

    /** The Character <pre>{@code '>'. }</pre>*/
    public static final Character GT = '>';

    /** The Character '&lt;'. */
    public static final Character LT = '<';

    /** The Character '?'. */
    public static final Character QUEST = '?';

    /** The Character '"'. */
    public static final Character QUOT = '"';

    /** The Character '/'. */
    public static final Character SLASH = '/';

    /**
     * Null attribute name
     */
    public static final String NULL_ATTR = "xsi:nil";

    public static final String TYPE_ATTR = "xsi:type";

    /**
     * Creates an iterator for navigating Code Points in a string instead of
     * characters. Once Java7 support is dropped, this can be replaced with
     * <code>
     * string.codePoints()
     * </code>
     * which is available in Java8 and above.
     *
     * @see <a href=
     *      "http://stackoverflow.com/a/21791059/6030888">http://stackoverflow.com/a/21791059/6030888</a>
     */
    private static Iterable<Integer> codePointIterator(final String string) {
        return new Iterable<Integer>() {
            @Override
            public Iterator<Integer> iterator() {
                return new Iterator<Integer>() {
                    private int nextIndex = 0;
                    private int length = string.length();

                    @Override
                    public boolean hasNext() {
                        return this.nextIndex < this.length;
                    }

                    @Override
                    public Integer next() {
                        int result = string.codePointAt(this.nextIndex);
                        this.nextIndex += Character.charCount(result);
                        return result;
                    }

                    @Override
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    /**
     * Replace special characters with XML escapes:
     *
     * <pre>{@code 
     * &amp; (ampersand) is replaced by &amp;amp;
     * &lt; (less than) is replaced by &amp;lt;
     * &gt; (greater than) is replaced by &amp;gt;
     * &quot; (double quote) is replaced by &amp;quot;
     * &apos; (single quote / apostrophe) is replaced by &amp;apos;
     * }</pre>
     *
     * @param string
     *            The string to be escaped.
     * @return The escaped string.
     */
    public static String escape(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (final int cp : codePointIterator(string)) {
            switch (cp) {
            case '&':
                sb.append("&amp;");
                break;
            case '<':
                sb.append("&lt;");
                break;
            case '>':
                sb.append("&gt;");
                break;
            case '"':
                sb.append("&quot;");
                break;
            case '\'':
                sb.append("&apos;");
                break;
            default:
                if (mustEscape(cp)) {
                    sb.append("&#x");
                    sb.append(Integer.toHexString(cp));
                    sb.append(';');
                } else {
                    sb.appendCodePoint(cp);
                }
            }
        }
        return sb.toString();
    }

    /**
     * @param cp code point to test
     * @return true if the code point is not valid for an XML
     */
    private static boolean mustEscape(int cp) {
        /* Valid range from https://www.w3.org/TR/REC-xml/#charsets
         *
         * #x9 | #xA | #xD | [#x20-#xD7FF] | [#xE000-#xFFFD] | [#x10000-#x10FFFF]
         *
         * any Unicode character, excluding the surrogate blocks, FFFE, and FFFF.
         */
        // isISOControl is true when (cp >= 0 && cp <= 0x1F) || (cp >= 0x7F && cp <= 0x9F)
        // all ISO control characters are out of range except tabs and new lines
        return (Character.isISOControl(cp)
                && cp != 0x9
                && cp != 0xA
                && cp != 0xD
            ) || !(
                // valid the range of acceptable characters that aren't control
                (cp >= 0x20 && cp <= 0xD7FF)
                || (cp >= 0xE000 && cp <= 0xFFFD)
                || (cp >= 0x10000 && cp <= 0x10FFFF)
            )
        ;
    }

    /**
     * Removes XML escapes from the string.
     *
     * @param string
     *            string to remove escapes from
     * @return string with converted entities
     */
    public static String unescape(String string) {
        StringBuilder sb = new StringBuilder(string.length());
        for (int i = 0, length = string.length(); i < length; i++) {
            char c = string.charAt(i);
            if (c == '&') {
                final int semic = string.indexOf(';', i);
                if (semic > i) {
                    final String entity = string.substring(i + 1, semic);
                    sb.append(XMLTokener.unescapeEntity(entity));
                    // skip past the entity we just parsed.
                    i += entity.length() + 1;
                } else {
                    // this shouldn't happen in most cases since the parser
                    // errors on unclosed entries.
                    sb.append(c);
                }
            } else {
                // not part of an entity
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Throw an exception if the string contains whitespace. Whitespace is not
     * allowed in tagNames and attributes.
     *
     * @param string
     *            A string.
     * @throws JSONException Thrown if the string contains whitespace or is empty.
     */
    public static void noSpace(String string) throws JSONException {
        int i, length = string.length();
        if (length == 0) {
            throw new JSONException("Empty string.");
        }
        for (i = 0; i < length; i += 1) {
            if (Character.isWhitespace(string.charAt(i))) {
                throw new JSONException("'" + string
                        + "' contains a space character.");
            }
        }
    }

    /**
     * Scan the content following the named tag, attaching it to the context.
     *
     * @param x
     *            The XMLTokener containing the source string.
     * @param context
     *            The JSONObject that will include the new material.
     * @param name
     *            The tag name.
     * @return true if the close tag is processed.
     * @throws JSONException
     */
    private static boolean parse(XMLTokener x, JSONObject context, String name, XMLParserConfiguration config) throws JSONException {
        char c;
        int i;
        JSONObject jsonObject = null;
        String string;
        String tagName;
        Object token;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter;

        // Test for and skip past these forms:
        // <!-- ... -->
        // <! ... >
        // <![ ... ]]>
        // <? ... ?>
        // Report errors for these forms:
        // <>
        // <=
        // <<

        token = x.nextToken();

        // <!
        /* if '!' we have a comment so skip */
        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;
                }
                x.back();
            }

            else if (c == '[') {
                token = x.nextToken();
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            context.accumulate(config.getcDataTagName(), string);
                        }
                        return false;
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        }

        /* if '?' skip all xml headers <??> */
        else if (token == QUEST) { // CLOSE

            // <?
            x.skipPast("?>");
            return false;
        }

        /* if '/' go to the token immediately following / which should be a tag name */
        else if (token == SLASH) {
            // Close tag </

            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;

        }

        /* idk */
        else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");

            // Open tag <

        }

        /* if the token is a tag name... */
        else {
            tagName = (String) token;
            token = null;
            jsonObject = new JSONObject();
            boolean nilAttributeFound = false;
            xmlXsiTypeConverter = null;

            for (;;) {
                if (token == null) {
                    token = x.nextToken();
                }
                //attribute = value

                /* idk */
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");
                        }

                        if (config.isConvertNilAttributeToNull()
                                && NULL_ATTR.equals(string)
                                && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;
                        }

                        else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                                && TYPE_ATTR.equals(string)) {
                            xmlXsiTypeConverter = config.getXsiTypeMap().get(token);
                        }

                        else if (!nilAttributeFound) {
                            jsonObject.accumulate(string,
                                    config.isKeepStrings()
                                            ? ((String) token)
                                            : stringToValue((String) token));
                        }
                        token = null;
                    }

                    else {
                        jsonObject.accumulate(string, "");
                    }


                }

                /* if the next token is '/' we have an empty tag*/
                else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (config.getForceList().contains(tagName)) {
                        // Force the value to be an array
                        if (nilAttributeFound) {
                            context.append(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(tagName, jsonObject);
                        } else {
                            context.put(tagName, new JSONArray());
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(tagName, jsonObject);
                        } else {
                            context.accumulate(tagName, "");
                        }
                    }
                    return false;

                }

                /* if next token is '>' */
                else if (token == GT) {
                    for (;;) {
                        token = x.nextContent();

                        /* idk */
                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;
                        }

                        /*  hits when above is a string value (contents inside tag) */
                        else if (token instanceof String) {
                            string = (String) token;
                            if (string.length() > 0) {
                                if(xmlXsiTypeConverter != null) {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                            stringToValue(string, xmlXsiTypeConverter));
                                }
                                else {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                            config.isKeepStrings() ? string : stringToValue(string));
                                }
                            }
                        }

                        /* hits when above is '<' */
                        else if (token == LT) {
                            // Nested element
                            if (parse(x, jsonObject, tagName, config)) {
                                if (config.getForceList().contains(tagName)) {
                                    // Force the value to be an array
                                    if (jsonObject.length() == 0) {
                                        context.put(tagName, new JSONArray());
                                    } else if (jsonObject.length() == 1 && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.append(tagName, jsonObject);
                                    }
                                } else {
                                    if (jsonObject.length() == 0) {
                                        context.accumulate(tagName, "");
                                    } else if (jsonObject.length() == 1 && jsonObject.opt(config.getcDataTagName()) != null) { /*  */
                                        context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.accumulate(tagName, jsonObject);
                                    }
                                }
                                return false;
                            }
                        }
                    }
                } //***

                else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }

    /**
     * This method tries to convert the given string value to the target object
     * @param string String to convert
     * @param typeConverter value converter to convert string to integer, boolean e.t.c
     * @return JSON value of this string or the string
     */
    public static Object stringToValue(String string, XMLXsiTypeConverter<?> typeConverter) {
        if(typeConverter != null) {
            return typeConverter.convert(string);
        }
        return stringToValue(string);
    }

    /**
     * This method is the same as {@link JSONObject#stringToValue(String)}.
     *
     * @param string String to convert
     * @return JSON value of this string or the string
     */
    // To maintain compatibility with the Android API, this method is a direct copy of
    // the one in JSONObject. Changes made here should be reflected there.
    // This method should not make calls out of the XML object.
    public static Object stringToValue(String string) {
        if ("".equals(string)) {
            return string;
        }

        // check JSON key words true/false/null
        if ("true".equalsIgnoreCase(string)) {
            return Boolean.TRUE;
        }
        if ("false".equalsIgnoreCase(string)) {
            return Boolean.FALSE;
        }
        if ("null".equalsIgnoreCase(string)) {
            return JSONObject.NULL;
        }

        /*
         * If it might be a number, try converting it. If a number cannot be
         * produced, then the value will just be a string.
         */

        char initial = string.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            try {
                return stringToNumber(string);
            } catch (Exception ignore) {
            }
        }
        return string;
    }
    
    /**
     * direct copy of {@link JSONObject#stringToNumber(String)} to maintain Android support.
     */
    private static Number stringToNumber(final String val) throws NumberFormatException {
        char initial = val.charAt(0);
        if ((initial >= '0' && initial <= '9') || initial == '-') {
            // decimal representation
            if (isDecimalNotation(val)) {
                // Use a BigDecimal all the time so we keep the original
                // representation. BigDecimal doesn't support -0.0, ensure we
                // keep that by forcing a decimal.
                try {
                    BigDecimal bd = new BigDecimal(val);
                    if(initial == '-' && BigDecimal.ZERO.compareTo(bd)==0) {
                        return Double.valueOf(-0.0);
                    }
                    return bd;
                } catch (NumberFormatException retryAsDouble) {
                    // this is to support "Hex Floats" like this: 0x1.0P-1074
                    try {
                        Double d = Double.valueOf(val);
                        if(d.isNaN() || d.isInfinite()) {
                            throw new NumberFormatException("val ["+val+"] is not a valid number.");
                        }
                        return d;
                    } catch (NumberFormatException ignore) {
                        throw new NumberFormatException("val ["+val+"] is not a valid number.");
                    }
                }
            }
            // block items like 00 01 etc. Java number parsers treat these as Octal.
            if(initial == '0' && val.length() > 1) {
                char at1 = val.charAt(1);
                if(at1 >= '0' && at1 <= '9') {
                    throw new NumberFormatException("val ["+val+"] is not a valid number.");
                }
            } else if (initial == '-' && val.length() > 2) {
                char at1 = val.charAt(1);
                char at2 = val.charAt(2);
                if(at1 == '0' && at2 >= '0' && at2 <= '9') {
                    throw new NumberFormatException("val ["+val+"] is not a valid number.");
                }
            }
            // integer representation.
            // This will narrow any values to the smallest reasonable Object representation
            // (Integer, Long, or BigInteger)
            
            // BigInteger down conversion: We use a similar bitLength compare as
            // BigInteger#intValueExact uses. Increases GC, but objects hold
            // only what they need. i.e. Less runtime overhead if the value is
            // long lived.
            BigInteger bi = new BigInteger(val);
            if(bi.bitLength() <= 31){
                return Integer.valueOf(bi.intValue());
            }
            if(bi.bitLength() <= 63){
                return Long.valueOf(bi.longValue());
            }
            return bi;
        }
        throw new NumberFormatException("val ["+val+"] is not a valid number.");
    }
    
    /**
     * direct copy of {@link JSONObject#isDecimalNotation(String)} to maintain Android support.
     */
    private static boolean isDecimalNotation(final String val) {
        return val.indexOf('.') > -1 || val.indexOf('e') > -1
                || val.indexOf('E') > -1 || "-0".equals(val);
    }


    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code 
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * @param string
     *            The source string.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string) throws JSONException {
        return toJSONObject(string, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code 
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * @param reader The XML source reader.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader) throws JSONException {
        return toJSONObject(reader, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param reader The XML source reader.
     * @param keepStrings If true, then values will not be coerced into boolean
     *  or numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader, boolean keepStrings) throws JSONException {
        if(keepStrings) {
            return toJSONObject(reader, XMLParserConfiguration.KEEP_STRINGS);
        }
        return toJSONObject(reader, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param reader The XML source reader.
     * @param config Configuration options for the parser
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(Reader reader, XMLParserConfiguration config) throws JSONException {
        JSONObject jo = new JSONObject();
        XMLTokener x = new XMLTokener(reader);
        while (x.more()) {
            x.skipPast("<");
            if(x.more()) {
                parse(x, jo, null, config);
            }
        }
        return jo;
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code 
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param string
     *            The source string.
     * @param keepStrings If true, then values will not be coerced into boolean
     *  or numeric values and will instead be left as strings
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string, boolean keepStrings) throws JSONException {
        return toJSONObject(new StringReader(string), keepStrings);
    }

    /**
     * Convert a well-formed (but not necessarily valid) XML string into a
     * JSONObject. Some information may be lost in this transformation because
     * JSON is a data format and XML is a document format. XML uses elements,
     * attributes, and content text, while JSON uses unordered collections of
     * name/value pairs and arrays of values. JSON does not does not like to
     * distinguish between elements and attributes. Sequences of similar
     * elements are represented as JSONArrays. Content text may be placed in a
     * "content" member. Comments, prologs, DTDs, and <pre>{@code 
     * &lt;[ [ ]]>}</pre>
     * are ignored.
     *
     * All values are converted as strings, for 1, 01, 29.0 will not be coerced to
     * numbers but will instead be the exact value as seen in the XML document.
     *
     * @param string
     *            The source string.
     * @param config Configuration options for the parser.
     * @return A JSONObject containing the structured data from the XML string.
     * @throws JSONException Thrown if there is an errors while parsing the string
     */
    public static JSONObject toJSONObject(String string, XMLParserConfiguration config) throws JSONException {
        return toJSONObject(new StringReader(string), config);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(Object object) throws JSONException {
        return toString(object, null, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName) {
        return toString(object, tagName, XMLParserConfiguration.ORIGINAL);
    }

    /**
     * Convert a JSONObject into a well-formed, element-normal XML string.
     *
     * @param object
     *            A JSONObject.
     * @param tagName
     *            The optional name of the enclosing tag.
     * @param config
     *            Configuration that can control output to XML.
     * @return A string.
     * @throws JSONException Thrown if there is an error parsing the string
     */
    public static String toString(final Object object, final String tagName, final XMLParserConfiguration config)
            throws JSONException {
        StringBuilder sb = new StringBuilder();
        JSONArray ja;
        JSONObject jo;
        String string;

        if (object instanceof JSONObject) {

            // Emit <tagName>
            if (tagName != null) {
                sb.append('<');
                sb.append(tagName);
                sb.append('>');
            }

            // Loop thru the keys.
            // don't use the new entrySet accessor to maintain Android Support
            jo = (JSONObject) object;
            for (final String key : jo.keySet()) {
                Object value = jo.opt(key);
                if (value == null) {
                    value = "";
                } else if (value.getClass().isArray()) {
                    value = new JSONArray(value);
                }

                // Emit content in body
                if (key.equals(config.getcDataTagName())) {
                    if (value instanceof JSONArray) {
                        ja = (JSONArray) value;
                        int jaLength = ja.length();
                        // don't use the new iterator API to maintain support for Android
						for (int i = 0; i < jaLength; i++) {
                            if (i > 0) {
                                sb.append('\n');
                            }
                            Object val = ja.opt(i);
                            sb.append(escape(val.toString()));
                        }
                    } else {
                        sb.append(escape(value.toString()));
                    }

                    // Emit an array of similar keys

                } else if (value instanceof JSONArray) {
                    ja = (JSONArray) value;
                    int jaLength = ja.length();
                    // don't use the new iterator API to maintain support for Android
					for (int i = 0; i < jaLength; i++) {
                        Object val = ja.opt(i);
                        if (val instanceof JSONArray) {
                            sb.append('<');
                            sb.append(key);
                            sb.append('>');
                            sb.append(toString(val, null, config));
                            sb.append("</");
                            sb.append(key);
                            sb.append('>');
                        } else {
                            sb.append(toString(val, key, config));
                        }
                    }
                } else if ("".equals(value)) {
                    sb.append('<');
                    sb.append(key);
                    sb.append("/>");

                    // Emit a new tag <k>

                } else {
                    sb.append(toString(value, key, config));
                }
            }
            if (tagName != null) {

                // Emit the </tagName> close tag
                sb.append("</");
                sb.append(tagName);
                sb.append('>');
            }
            return sb.toString();

        }

        if (object != null && (object instanceof JSONArray ||  object.getClass().isArray())) {
            if(object.getClass().isArray()) {
                ja = new JSONArray(object);
            } else {
                ja = (JSONArray) object;
            }
            int jaLength = ja.length();
            // don't use the new iterator API to maintain support for Android
			for (int i = 0; i < jaLength; i++) {
                Object val = ja.opt(i);
                // XML does not have good support for arrays. If an array
                // appears in a place where XML is lacking, synthesize an
                // <array> element.
                sb.append(toString(val, tagName == null ? "array" : tagName, config));
            }
            return sb.toString();
        }

        string = (object == null) ? "null" : escape(object.toString());
        return (tagName == null) ? "\"" + string + "\""
                : (string.length() == 0) ? "<" + tagName + "/>" : "<" + tagName
                        + ">" + string + "</" + tagName + ">";

    }


    /* return a subsection of XML using the path*/
    public static String getXMLAtPath(XMLTokener x, List<String> pathTokens) {
        Object token;
        int indPathTokens = 0;
        int pathsMatched = 0;

        while (x.more()){
            x.skipPast("<");
            if (x.more()) {

                token = x.nextToken(); // will return '?' '>' '!' '/' or an xml tag name, we only care about tag names


                // if the tag name returned by nextToken() is an entry in the path, increment matches
                if (token.toString().equals(pathTokens.get(indPathTokens))) {
                    indPathTokens++;
                    pathsMatched++;

                    // when we navigate to the final path, extract a string of the object
                    // to reduce memory usage, do not continue parsing the rest of the file
                    if (pathsMatched == pathTokens.size()) {
                        return parseAtPath(x, pathTokens.get(--indPathTokens));
                    }
                }
            }
        }
        throw new JSONPointerException("path not found");
    }


    /* returns a string representation of an XML tag */
    private static String parseAtPath(XMLTokener x, String tagName) {
        char currentChar;
        StringBuilder xmlBuilder = new StringBuilder();

        // append the tag to the builder
        xmlBuilder.append("<").append(tagName);

        while (x.more()) {
            currentChar = x.next();
            xmlBuilder.append(currentChar);
            if (currentChar == '>' && xmlBuilder.toString().endsWith("/" + tagName + ">")) {
                return xmlBuilder.toString();
            }
        }

        throw x.syntaxError("no closing tag found for <" + tagName + ">");
    }


    /**
     * Converts a specified subsection of XML into JSON using the key path
     * @param reader the reader for our XML input
     * @param path the path to the subsection to be extracted and converted
     * @return the subsection of XML converted to JSON
     *
     * @authors Trent Lilley, Joseph Lee
     */
    public static JSONObject toJSONObject(Reader reader, JSONPointer path) {
        XMLTokener x = new XMLTokener(reader);
        JSONObject subObject;
        List<String> pathTokens = path.refTokens;
        String subXML;

        pathTokens.remove(pathTokens.size() - 1);
        subXML = getXMLAtPath(x, pathTokens);
        subObject = XML.toJSONObject(subXML);

        return subObject;
    }


    /**
     * Converts XML to JSON replacing a sub-section of the JSON with a specified replacement
     * @param reader the reader for our XML input
     * @param path the path to the object to replace
     * @param replacement the replacement json object
     * @return a json object with the replaced content
     * @throws IOException
     *
     * @authors Trent Lilley, Joseph Lee
     */
    public static JSONObject toJSONObject(StringReader reader, JSONPointer path, JSONObject replacement) throws IOException {
        JSONObject jo = new JSONObject();
        XMLTokener x = new XMLTokener(reader);
        XMLParserConfiguration config = new XMLParserConfiguration().withKeepStrings(true);
        int pathsMatched = 0;
        List<String> pathTokens = path.refTokens;
        pathTokens.remove(pathTokens.size() - 1);

        while (x.more()) {
            x.skipPast("<");
            if(x.more()) {
                parseAndReplace(x, jo, null, pathTokens, replacement, config, pathsMatched);
            }
        }
        return jo;
    }

    /* recursive replace function based on parse()*/
    private static boolean parseAndReplace(XMLTokener x, JSONObject context, String name, List<String> pathTokens, JSONObject replacement, XMLParserConfiguration config, int pathsMatched) {
        char c;
        int i;
        JSONObject jsonObject = null;
        String string;
        String tagName;
        Object token;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter;

        token = x.nextToken();


        /* if '!' we have a comment so skip */
        if (token == BANG) {
            c = x.next();
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;
                }
                x.back();
            }

            else if (c == '[') {
                token = x.nextToken();
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            context.accumulate(config.getcDataTagName(), string);
                        }
                        return false;
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        }

        /* if '?' skip all xml headers <??> */
        else if (token == QUEST) { // CLOSE
            // <?
            x.skipPast("?>");
            return false;
        }

        /* if '/' go to the token immediately following / which should be a tag name */
        else if (token == SLASH) {
            // Close tag </

            token = x.nextToken();
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;

        }

        else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");

            // Open tag <

        }

        /* if the token is a tag name... */
        else {
            tagName = (String) token;
            token = null;
            jsonObject = new JSONObject();
            boolean nilAttributeFound = false;
            xmlXsiTypeConverter = null;

            if (pathsMatched != pathTokens.size() && tagName.equals(pathTokens.get(pathsMatched))) pathsMatched++;

            for (;;) {
                if (token == null) {
                    token = x.nextToken();
                }
                //attribute = value


                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");
                        }

                        if (config.isConvertNilAttributeToNull()
                            && NULL_ATTR.equals(string)
                            && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;
                        }

                        else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                            && TYPE_ATTR.equals(string)) {
                            xmlXsiTypeConverter = config.getXsiTypeMap().get(token);
                        }

                        else if (!nilAttributeFound) {
                            jsonObject.accumulate(string,
                                config.isKeepStrings()
                                    ? ((String) token)
                                    : stringToValue((String) token));
                        }
                        token = null;
                    }

                    else {
                        jsonObject.accumulate(string, "");
                    }


                }

                /* if the next token is '/' we have an empty tag */
                else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (config.getForceList().contains(tagName)) {
                        // Force the value to be an array
                        if (nilAttributeFound) {
                            context.append(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(tagName, jsonObject);
                        } else {
                            context.put(tagName, new JSONArray());
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(tagName, jsonObject);
                        } else {
                            context.accumulate(tagName, "");
                        }
                    }
                    return false;
                }

                /* if next token is '>' */
                else if (token == GT) {
                    for (;;) {
                        token = x.nextContent();

                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;
                        }

                        /*  hits when above is a string value (contents inside tag) */
                        else if (token instanceof String) {
                            string = (String) token;
                            if (string.length() > 0) {
                                if(xmlXsiTypeConverter != null) {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                        stringToValue(string, xmlXsiTypeConverter));
                                }
                                else {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                        config.isKeepStrings() ? string : stringToValue(string));
                                }
                            }
                        }

                        /* hits when above is '<' */
                        else if (token == LT) {
                            // Nested element
                            if (parseAndReplace(x, jsonObject, tagName, pathTokens, replacement, config, pathsMatched)) {

                                if (config.getForceList().contains(tagName)) {
                                    // Force the value to be an array
                                    if (jsonObject.length() == 0) {
                                        context.put(tagName, new JSONArray());
                                    } else if (jsonObject.length() == 1 && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.append(tagName, jsonObject);
                                    }
                                }

                                else {
                                    if (jsonObject.length() == 0) {
                                        context.accumulate(tagName, "");
                                    }

                                    // REPLACE: for single key value replacements
                                    else if (jsonObject.length() == 1 && jsonObject.opt(config.getcDataTagName()) != null) { /*  */
                                        Object value = jsonObject.opt(config.getcDataTagName());

                                        if (pathsMatched == pathTokens.size() && tagName.equals(pathTokens.get(pathTokens.size() - 1))) {
                                            List<String> replacementTokens = Arrays.asList(replacement.toString().split("[{}:\"]"));
                                            List<String> keyval = replacementTokens.stream().filter(w -> !w.equals("")).collect(Collectors.toList());
                                            tagName = keyval.get(0);
                                            value = keyval.get(1);
                                        }

                                        context.accumulate(tagName, value);
                                    }

                                    else {
                                        // REPLACE: larger object replacement (nested objects)
                                        if (pathsMatched == pathTokens.size() && tagName.equals(pathTokens.get(pathTokens.size() - 1))) {
                                            StringBuilder updatedJsonObjectBuilder = new StringBuilder();
                                            JSONTokener j = new JSONTokener(String.valueOf(replacement));
                                            j.skipTo(':');
                                            j.skipTo('{');

                                            while (j.more()) {
                                                updatedJsonObjectBuilder.append(j.next());
                                            }
                                            updatedJsonObjectBuilder.deleteCharAt(updatedJsonObjectBuilder.length() - 1);
                                            JSONObject updatedJson = new JSONObject(updatedJsonObjectBuilder.toString());
                                            jsonObject = updatedJson;
                                        }
                                        context.accumulate(tagName, jsonObject);
                                    }
                                }
                                return false;
                            }
                        }
                    }
                }

                else {
                    throw x.syntaxError("Misshaped tag");
                }
            }
        }
    }


    public static JSONObject toJSONObject(Reader reader, Function<String, String> keyTransformer) {
        JSONObject jo = new JSONObject();
        XMLTokener x = new XMLTokener(reader);
        XMLParserConfiguration config = new XMLParserConfiguration().withKeepStrings(true);

        while (x.more()) {
            x.skipPast("<");
            if (x.more()) {
                replaceKeys(x, jo, keyTransformer, null, config);
            }

        } // METHOD LOOP

        return jo;
    }

    private static boolean replaceKeys(XMLTokener x, JSONObject context, Function<String, String> keyTransformer, String name, XMLParserConfiguration config) throws JSONException {
        char c;
        int i;
        JSONObject jsonObject = null;
        String string;
        String tagName;
        Object token;
        XMLXsiTypeConverter<?> xmlXsiTypeConverter;

        token = x.nextToken();
//        System.out.println("TOKEN: " + token);

        /* [1]case '!': we have a comment */
        if (token == BANG) {
            c = x.next();

            // skip past comments
            if (c == '-') {
                if (x.next() == '-') {
                    x.skipPast("-->");
                    return false;
                }
                x.back();
            }

            // handle CDATA
            else if (c == '[') {
                token = x.nextToken();
                if ("CDATA".equals(token)) {
                    if (x.next() == '[') {
                        string = x.nextCDATA();
                        if (string.length() > 0) {
                            context.accumulate(config.getcDataTagName(), string);
                        }
                        return false;
                    }
                }
                throw x.syntaxError("Expected 'CDATA['");
            }
            i = 1;
            do {
                token = x.nextMeta();
                if (token == null) {
                    throw x.syntaxError("Missing '>' after '<!'.");
                } else if (token == LT) {
                    i += 1;
                } else if (token == GT) {
                    i -= 1;
                }
            } while (i > 0);
            return false;
        } // END '!' CASE

        /* [1]case '?': skip all xml headers */
        else if (token == QUEST) { // CLOSE
//            System.out.println("  pass");
            x.skipPast("?>");
            return false;
        } // END '?' CASE

        /* [1]case '/': check for errors in closing tag, go to next recursion */
        else if (token == SLASH) {
            token = x.nextToken();
//            System.out.println("  passed"); // DEBUG
            if (name == null) {
                throw x.syntaxError("Mismatched close tag " + token);
            }
            if (!token.equals(name)) {
                throw x.syntaxError("Mismatched " + name + " and " + token);
            }
            if (x.nextToken() != GT) {
                throw x.syntaxError("Misshaped close tag");
            }
            return true;
        } // END '/' CASE

        /* [2]case: any other char, we may have bad XML */
        else if (token instanceof Character) {
            throw x.syntaxError("Misshaped tag");

        } // END OTHER CASE

        /* [1]default: we have an opening tag name */
        else {
            tagName = (String) token;
            token = null;
            jsonObject = new JSONObject();
            boolean nilAttributeFound = false;
            xmlXsiTypeConverter = null;

            for (;;) {
                if (token == null) {
                    token = x.nextToken();
//                    System.out.println("  next: |" + tagName + "| " + token);
                }

                /* [2]Case not '/' or '>': ??? */
                if (token instanceof String) {
                    string = (String) token;
                    token = x.nextToken();
//                    System.out.println("  next: |" + tagName + "| |" + string + "| " + token);
                    if (token == EQ) {
                        token = x.nextToken();
                        if (!(token instanceof String)) {
                            throw x.syntaxError("Missing value");
                        }

                        if (config.isConvertNilAttributeToNull()
                            && NULL_ATTR.equals(string)
                            && Boolean.parseBoolean((String) token)) {
                            nilAttributeFound = true;
                        }

                        else if(config.getXsiTypeMap() != null && !config.getXsiTypeMap().isEmpty()
                            && TYPE_ATTR.equals(string)) {
                            xmlXsiTypeConverter = config.getXsiTypeMap().get(token);
                        }

                        else if (!nilAttributeFound) {
                            jsonObject.accumulate(string,
                                config.isKeepStrings()
                                    ? ((String) token)
                                    : stringToValue((String) token));
                        }
                        token = null;
                    }

                    else {
                        jsonObject.accumulate(string, "");
                    }


                }

                /* [2]Case '/': we have an empty tag */
                else if (token == SLASH) {
                    // Empty tag <.../>
                    if (x.nextToken() != GT) {
                        throw x.syntaxError("Misshaped tag");
                    }
                    if (config.getForceList().contains(tagName)) {
                        // Force the value to be an array
                        if (nilAttributeFound) {
                            context.append(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.append(tagName, jsonObject);
                        } else {
                            context.put(tagName, new JSONArray());
                        }
                    } else {
                        if (nilAttributeFound) {
                            context.accumulate(tagName, JSONObject.NULL);
                        } else if (jsonObject.length() > 0) {
                            context.accumulate(tagName, jsonObject);
                        } else {
                            context.accumulate(tagName, "");
                        }
                    }
                    return false;
                }

                /* [2]Case '>': opening tag close, now accessing content within tags */
                else if (token == GT) {
                    for (;;) {
                        token = x.nextContent();
//                        System.out.println("  next: |" + tagName + "| |>| " + token) ;

                        if (token == null) {
                            if (tagName != null) {
                                throw x.syntaxError("Unclosed tag " + tagName);
                            }
                            return false;
                        }

                        /*  hits when above is a string value (contents inside tag) */
                        else if (token instanceof String) {
                            string = (String) token;
                            if (string.length() > 0) {
                                if(xmlXsiTypeConverter != null) {
                                    jsonObject.accumulate(config.getcDataTagName(),
                                        stringToValue(string, xmlXsiTypeConverter));
                                }
                                else {
//                                    System.out.println("  accumulate: " + config.getcDataTagName() + ", " + string);

                                    // KEY TRANSFORM
                                    String key = null;
                                    if (keyTransformer != null) {
                                        key = keyTransformer.apply(config.getcDataTagName());
                                    }

                                    jsonObject.accumulate(key, config.isKeepStrings() ? string : stringToValue(string));
                                    // END KEY TRANSFORM
                                }
                            }
                        }

                        /* hits when above is '<' */
                        else if (token == LT) {
                            // Nested element
//                            System.out.println("  ---recurse");
                            if (replaceKeys(x, jsonObject, keyTransformer, tagName, config)) {
//                                System.out.println("  ---recursed true");
                                if (config.getForceList().contains(tagName)) {
                                    // Force the value to be an array
                                    if (jsonObject.length() == 0) {
                                        context.put(tagName, new JSONArray());
                                    } else if (jsonObject.length() == 1 && jsonObject.opt(config.getcDataTagName()) != null) {
                                        context.append(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
                                        context.append(tagName, jsonObject);
                                    }
                                } else {
                                    if (jsonObject.length() == 0) {
                                        context.accumulate(tagName, "");
                                    } else if (jsonObject.length() == 1 && jsonObject.opt(config.getcDataTagName()) != null) { /*  */
                                        context.accumulate(tagName, jsonObject.opt(config.getcDataTagName()));
                                    } else {
//                                        System.out.println("  TEST: " + tagName + ", " + jsonObject);

                                        // KEY TRANSFORM
                                        String key = null;
                                        if (keyTransformer != null) {
                                            key = keyTransformer.apply(tagName);
                                        }

                                        context.accumulate(key, jsonObject);

                                        // END KEY TRANSFORM
                                    }
                                }
                                return false;
                            }
                        }
                    }
                }
                else {
                    throw x.syntaxError("Misshaped tag");
                } //END [2]
//                System.out.println("  exit method");
            }
        } //END [1]
    } // END METHOD



    /**
     * Asynchronous method that returns a future JSONObject and performs a specified
     * operation on that object before returning to the caller
     */
    public static Future<JSONObject> toJSONObjectAsync(Reader reader, Function<JSONObject, JSONObject> operation) {
        CompletableFuture<JSONObject> futureJson = new CompletableFuture<>();
        new Thread(() -> {
            try {
                JSONObject jsonObj = XML.toJSONObject(reader);
                jsonObj = operation.apply(jsonObj);
                futureJson.complete(jsonObj);
            }
            catch (Exception e) {
                // ensures error is not confined to thread, prevents indefinite blocking
                futureJson.completeExceptionally(e);
            }
        }).start();

        return futureJson;
    }
}
