/*
 *  Copyright 2014 Carnegie Mellon University
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.cmu.lti.oaqa.annographix.util;

import java.util.regex.Pattern;

import org.apache.lucene.analysis.standard.StandardAnalyzer;

/**
 * 
 * The file with various constants and helper functions.
 * 
 * @author Leonid Boytsov
 *
 */
public class UtilConst {
  /**
   *   A version of Lucene/SOLR library to use.
   */
  public static final String LUCENE_VERSION = "4.6";
  /**
   * The name of the field with annotations (in SOLR).
   */
  public static final String DEFAULT_ANNOT_FIELD = "Annotation";
  /**
   * The name of the annotation field tokenizer.
   *
   */
  public static Object ANNOT_FIELD_TOKENIZER = "solr.WhitespaceTokenizerFactory";
  
  /**
   * The name of the annotated text field (in SOLR).
   */
  public static final String DEFAULT_TEXT4ANNOT_FIELD  = "Text4Annotation";
  /**
   * The name of the id field (in SOLR).
   */  
  public static final String ID_FIELD = "Id";

  public static final String SCORE_FIELD = "score";
  
  /**
   * The string used to denote any-term (a wildcard).
   * It should not contain a PAYLOAD_CHAR or a PAYLOAD_ID_SEP_CHAR!
   * 
   */
  public static final String STRING_ANY = "#any";
  
  
  /**
   * We need to limit the maximum number of characters in a token.
   * Otherwise, payload descriptions will be truncated by Solr.
   */
  public static final int MAX_WORD_LEN = Math.min(128,
                                     StandardAnalyzer.DEFAULT_MAX_TOKEN_LENGTH);
  
  /**
   * Separates a payload description from respective keyword.
   * <p>
   *  Must match a payload configuration in the schema.xml file.
   *  This line would look like:
   *  </p>
   *  <p>
   * <filter class="solr.DelimitedPayloadTokenFilterFactory" delimiter="|" ...    
   *    
   */
  public static final char PAYLOAD_CHAR = '|';
  
  /**
   * Separates data elements in a payload description.
   */
  
  public static final char PAYLOAD_ID_SEP_CHAR     = '~';
  public static final String PAYLOAD_ID_SEP_CHAR_STR = "" + PAYLOAD_ID_SEP_CHAR ;
  
  
   /**
    * This character is used to combine several parts of 
    * annotation description (in the artificial, hidden, query).
    */
  public static final char VALUE_SEPARATOR = '_';
  public static final String VALUE_SEPARATOR_STR = VALUE_SEPARATOR + "";  
  
  public static final Pattern PATTERN_WHITESPACE = Pattern.compile("[\\s\n\r\t]");  
  
  /**
   * Replace whitespaces with regular spaces.
   */
  public static String replaceWhiteSpaces(String str) {
    return PATTERN_WHITESPACE.matcher(str).replaceAll(" ");
  }
  
  /**
   *  A configuration parameter that defines a current UIMA view
   *  for an annotator.
   */
  public static final String CONFIG_VIEW_NAME = "view_name";
  
  /**
   * A configuration parameter that defines a name of a stop-word
   * file.
   */
  public static final String CONFIG_STOPWORDS_FILE = "stopwords_file";

  /**
   *  A configuration parameter that defines a name of a annotated text field.
   */
  public static final String CONFIG_TEXT4ANNOT_FIELD = "text_field";

  /**
   *  A configuration parameter that defines a name of an annotation field
   */
  public static final String CONFIG_ANNOTATION_FIELD = "annot_field";  
  
  
  /**
   * 
   * This class holds keeps common constants and procedures
   * that are used for indexing.
   * 
   */

   /*
    * This function replace all whitespace characters with regular spaces.
    * We also replace payload separators, or else the payload parse may fail.
    */
  public static final Pattern PATTERN_PREPARE_PAYLOAD = Pattern.compile("[\\s" + 
                                                    UtilConst.PAYLOAD_CHAR + 
                                                    UtilConst.PAYLOAD_ID_SEP_CHAR + "]");
  /**
   * Query operators.
   */
  public static final String QUERY_CHILDOF      = "childof";
  public static final String QUERY_PARENTOF     = "parentof";
  public static final String QUERY_INSIDE       = "inside";
  public static final String QUERY_CONTAINS     = "contains";
  public static final String QUERY_NEXUS        = "nexus";
  
  /** end query operators **/

  public static final String INDEX_DOC_ENTRY = "DOC";
  public static final String INDEX_DOCNO     = "DOCNO";
  
  public static final String USER_AGENT = "Mozilla/4.0";
  
  public static String preparePayloadToken(String annotText) {
    return PATTERN_PREPARE_PAYLOAD.matcher(annotText).replaceAll(" ").
                                      // Stanford morphology chokes on '_'!
                                      replace('_', ' ').
                                      // Remove some punctuation
                                      replaceAll("\\p{P}", " ");
  }
  
  /**
   *  Replaces bad Unicode characters, but doesn't change the string length!
   *  <p>
   *  Based on this solution:
   *  http://stackoverflow.com/questions/20762/how-do-you-remove-invalid-hexadecimal-characters-from-an-xml-based-data-source-p
   *  </p>
   *  @param inString   input string
   *  @return an output string (will have the same length as inString).
   */
  public static String removeBadUnicode(String inString) {
    StringBuilder newString = new StringBuilder();
    char ch;

    for (int i = 0; i < inString.length(); i++) {
      ch = inString.charAt(i);
      /*
       *  Replace any characters outside the valid UTF-8 range 
       *  as well as all control characters with the space except 
       *  tabs and new lines.
       *  
       *  Don't delete any chars, we need to preserve word lengths 
       *  and positions!
       *  
       */
      if ((ch < 0x00FD && ch > 0x001F) 
          || ch == '\t' || ch == '\n' || ch == '\r') {
        newString.append(ch);
      } else {
        newString.append(' ');
      }
    }    
    return newString.toString();
  }  
  
  /**
   *  Creates an artificial term by merging strings representing
   *  an annotation type and an annotation label.
   *  
   * @param annotationType          annotation type, e.g., NamedEntity.
   * @param annotationLabel         annotation label, e.g. PER.
   * @return complete keyword representing the annotation   */

  public static String combineFieldValue(String annotationType, 
                                         String annotationLabel) {
    return annotationType + UtilConst.VALUE_SEPARATOR + annotationLabel; 
  }
  /**
   * 
   * Creates an artificial term annotation keyword  
   * by merging strings representing the following: 
   * annotation type (e.g., NamedEntity), 
   * annotation value (e.g. PER), 
   * term (e.g., Bill).
   * 
   * @param annotationType          annotation type, e.g., NamedEntity.
   * @param annotationLabel         annotation label, e.g. PER.
   * @param term
   * @return complete keyword representing the annotation
   */
  public static String combineFieldValue(String annotationType, 
                                         String annotationLabel,
                                         String term) {
    return annotationType + UtilConst.VALUE_SEPARATOR +  
           annotationLabel + UtilConst.VALUE_SEPARATOR +
           term; 
  }
}