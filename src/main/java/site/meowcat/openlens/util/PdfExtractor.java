package site.meowcat.openlens.util;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;

import java.io.InputStream;
import java.net.URL;

public class PdfExtractor {

    public static String extractText(String url) {
        try (InputStream input = new URL(url).openStream();
             PDDocument document = Loader.loadPDF(input.readAllBytes())) {

            PDFTextStripper stripper = new PDFTextStripper();
            return stripper.getText(document);

        } catch (Exception e) {
            System.err.println("PDF extract failed: " + url);
            e.printStackTrace();
            return "";
        }
    }
}