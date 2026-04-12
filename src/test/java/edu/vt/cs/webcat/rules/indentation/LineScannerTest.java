package edu.vt.cs.webcat.rules.indentation;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LineScannerTest {

    @Nested
    class LeadingSpaces {
        @Test
        void noLeadingSpaces() {
            assertEquals(0, LineScanner.countLeadingSpaces("hello"));
        }

        @Test
        void fourLeadingSpaces() {
            assertEquals(4, LineScanner.countLeadingSpaces("    hello"));
        }

        @Test
        void onlySpaces() {
            assertEquals(5, LineScanner.countLeadingSpaces("     "));
        }

        @Test
        void emptyString() {
            assertEquals(0, LineScanner.countLeadingSpaces(""));
        }
    }

    @Nested
    class TabDetection {
        @Test
        void noTab() {
            assertFalse(LineScanner.hasTabInIndentation("    hello"));
        }

        @Test
        void tabAtStart() {
            assertTrue(LineScanner.hasTabInIndentation("\thello"));
        }

        @Test
        void tabAfterSpaces() {
            assertTrue(LineScanner.hasTabInIndentation("  \thello"));
        }

        @Test
        void tabAfterCode() {
            assertFalse(LineScanner.hasTabInIndentation("hello\tworld"));
        }

        @Test
        void emptyLine() {
            assertFalse(LineScanner.hasTabInIndentation(""));
        }
    }

    @Nested
    class LineSplitting {
        @Test
        void singleLine() {
            List<LineInfo> lines = LineScanner.scan("hello");
            assertEquals(1, lines.size());
            assertEquals(1, lines.get(0).getLineNumber());
            assertEquals("hello", lines.get(0).getText());
        }

        @Test
        void multipleLines() {
            List<LineInfo> lines = LineScanner.scan("a\nb\nc");
            assertEquals(3, lines.size());
            assertEquals(1, lines.get(0).getLineNumber());
            assertEquals(2, lines.get(1).getLineNumber());
            assertEquals(3, lines.get(2).getLineNumber());
        }

        @Test
        void preservesTrailingEmptyLine() {
            List<LineInfo> lines = LineScanner.scan("a\n");
            assertEquals(2, lines.size());
            assertTrue(lines.get(1).isBlank());
        }
    }

    @Nested
    class BlankLines {
        @Test
        void emptyLineIsBlank() {
            List<LineInfo> lines = LineScanner.scan("a\n\nb");
            assertTrue(lines.get(1).isBlank());
        }

        @Test
        void whitespaceOnlyIsBlank() {
            List<LineInfo> lines = LineScanner.scan("a\n   \nb");
            assertTrue(lines.get(1).isBlank());
        }

        @Test
        void nonBlankIsNotBlank() {
            List<LineInfo> lines = LineScanner.scan("hello");
            assertFalse(lines.get(0).isBlank());
        }
    }

    @Nested
    class CommentDetection {
        @Test
        void lineComment() {
            List<LineInfo> lines = LineScanner.scan("// comment");
            assertTrue(lines.get(0).isCommentOnly());
        }

        @Test
        void indentedLineComment() {
            List<LineInfo> lines = LineScanner.scan("    // comment");
            assertTrue(lines.get(0).isCommentOnly());
        }

        @Test
        void singleLineBlockComment() {
            List<LineInfo> lines = LineScanner.scan("/* comment */");
            assertTrue(lines.get(0).isCommentOnly());
        }

        @Test
        void codeIsNotComment() {
            List<LineInfo> lines = LineScanner.scan("int x = 1;");
            assertFalse(lines.get(0).isCommentOnly());
        }
    }

    @Nested
    class BlockComments {
        @Test
        void multiLineBlockCommentInterior() {
            List<LineInfo> lines = LineScanner.scan("/*\n * inside\n */");
            assertTrue(lines.get(0).isCommentOnly());
            assertTrue(lines.get(1).isInsideBlockComment());
            assertTrue(lines.get(2).isInsideBlockComment());
        }

        @Test
        void javadocInterior() {
            List<LineInfo> lines = LineScanner.scan("/**\n * javadoc\n */");
            assertTrue(lines.get(0).isCommentOnly());
            assertTrue(lines.get(1).isInsideJavadoc());
            assertTrue(lines.get(2).isInsideJavadoc());
        }

        @Test
        void codeAfterBlockComment() {
            List<LineInfo> lines = LineScanner.scan("/**/\nint x;");
            assertFalse(lines.get(1).isInsideBlockComment());
        }
    }

    @Nested
    class TextBlocks {
        @Test
        void textBlockInteriorDetected() {
            String source = "String s = \"\"\"\n    hello\n    world\n    \"\"\";";
            List<LineInfo> lines = LineScanner.scan(source);
            assertTrue(lines.get(1).isInsideTextBlock());
            assertTrue(lines.get(2).isInsideTextBlock());
        }

        @Test
        void textBlockClosingLineStillMarkedAsInterior() {
            String source = "String s = \"\"\"\n    hello\n    \"\"\";";
            List<LineInfo> lines = LineScanner.scan(source);
            assertTrue(lines.get(1).isInsideTextBlock());
            assertTrue(lines.get(2).isInsideTextBlock());
        }

        @Test
        void codeAfterTextBlockNotMarked() {
            String source = "String s = \"\"\"\n    hello\n    \"\"\";\nint x = 1;";
            List<LineInfo> lines = LineScanner.scan(source);
            assertFalse(lines.get(3).isInsideTextBlock());
        }
    }

    @Nested
    class StarLines {
        @Test
        void starLineInBlockCommentIsCommentOnly() {
            List<LineInfo> lines = LineScanner.scan("/*\n * star line\n */");
            assertTrue(lines.get(1).isCommentOnly() || lines.get(1).isInsideBlockComment());
        }

        @Test
        void starLineOutsideBlockCommentTreatedAsComment() {
            List<LineInfo> lines = LineScanner.scan("* star line");
            assertTrue(lines.get(0).isCommentOnly());
        }
    }

    @Nested
    class EmptyFile {
        @Test
        void emptyFileProducesSingleBlankLine() {
            List<LineInfo> lines = LineScanner.scan("");
            assertEquals(1, lines.size());
            assertTrue(lines.get(0).isBlank());
        }
    }

    @Nested
    class SingleLineJavadoc {
        @Test
        void singleLineJavadocIsComment() {
            List<LineInfo> lines = LineScanner.scan("/** javadoc */");
            assertTrue(lines.get(0).isCommentOnly());
        }

        @Test
        void codeAfterSingleLineJavadoc() {
            List<LineInfo> lines = LineScanner.scan("/** javadoc */\nint x = 1;");
            assertTrue(lines.get(0).isCommentOnly());
            assertFalse(lines.get(1).isCommentOnly());
            assertFalse(lines.get(1).isInsideJavadoc());
        }
    }

    @Nested
    class MixedContent {
        @Test
        void tabsCountedInLeadingSpacesStopsAtTab() {
            assertEquals(2, LineScanner.countLeadingSpaces("  \thello"));
        }

        @Test
        void leadingSpacesOnlyCountsSpaces() {
            assertEquals(0, LineScanner.countLeadingSpaces("\thello"));
        }

        @Test
        void multipleBlockComments() {
            String source = "/* a */\nint x;\n/* b */\nint y;";
            List<LineInfo> lines = LineScanner.scan(source);
            assertTrue(lines.get(0).isCommentOnly());
            assertFalse(lines.get(1).isCommentOnly());
            assertTrue(lines.get(2).isCommentOnly());
            assertFalse(lines.get(3).isCommentOnly());
        }
    }
}

