package ru.samolet2003.itext;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.time.StopWatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.Rectangle;
import com.itextpdf.text.pdf.AcroFields;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.ColumnText;
import com.itextpdf.text.pdf.PdfContentByte;
import com.itextpdf.text.pdf.PdfCopy;
import com.itextpdf.text.pdf.PdfReader;
import com.itextpdf.text.pdf.PdfStamper;
import com.itextpdf.text.pdf.PdfWriter;

public class PdfFormFiller {
    private static final Logger log = LoggerFactory.getLogger(PdfFormFiller.class);
    private static final int DEFAULT_FONT_SIZE = 12;
    private static final int BOXED_TEXT_FONT_SIZE = 8;
    private static final String TEMPLATE_RESOURCE = "/Template.pdf";
    private final FormModel model;
    private final SimpleDateFormat simpleDateFormat = new SimpleDateFormat("ddMMyy");
    private PdfContentByte cb;
    private BaseFont baseFont;
    private AcroFields form;

    public PdfFormFiller(FormModel model) {
        this.model = model;
    }

    private void createPdf(OutputStream out) {
        InputStream template = getClass().getResourceAsStream(TEMPLATE_RESOURCE);
        if (template == null)
            throw new IllegalStateException("Template " + TEMPLATE_RESOURCE + " not found");
        try {
            baseFont = BaseFont.createFont("times.ttf", BaseFont.IDENTITY_H, BaseFont.NOT_EMBEDDED);
            PdfReader reader = new PdfReader(template);
            PdfStamper stamper = new PdfStamper(reader, out);
            form = stamper.getAcroFields();
            form.addSubstitutionFont(baseFont);
            cb = stamper.getOverContent(1);
            cb.beginText();
            cb.setFontAndSize(baseFont, DEFAULT_FONT_SIZE);
            firstPage();
            cb.endText();
            cb = stamper.getOverContent(2);
            cb.beginText();
            cb.setFontAndSize(baseFont, DEFAULT_FONT_SIZE);
            secondPage();
            cb.endText();
            stamper.setFormFlattening(true);
            stamper.close();
            reader.close();
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (DocumentException e) {
            throw new RuntimeException(e);
        }
    }

    private float getStringWidth(String s) {
        return cb.getEffectiveStringWidth(s, true);
    }

    public static void createPdf(List<FormModel> models, OutputStream out) {
        try {
            if (models.isEmpty()) {
                // создаём пустой документ
                Document document = new Document();
                PdfWriter writer = PdfWriter.getInstance(document, out);
                document.open();
                document.newPage();
                writer.setPageEmpty(false);
                document.close();
                writer.close();
                return;
            }
            StopWatch sw = new StopWatch();
            sw.start();
            Document document = new Document();
            PdfCopy copy = new PdfCopy(document, out);
            document.open();
            ByteArrayOutputStream tmpOut = new ByteArrayOutputStream(800000);
            int counter = 0;
            for (FormModel model : models) {
                tmpOut.reset();
                new PdfFormFiller(model).createPdf(tmpOut);
                PdfReader reader = new PdfReader(tmpOut.toByteArray());
                int n = reader.getNumberOfPages();
                for (int page = 0; page < n;) {
                    copy.addPage(copy.getImportedPage(reader, ++page));
                }
                copy.freeReader(reader);
                reader.close();
                counter++;
                if (sw.getTime() > 10000 && counter % 10 == 0) {
                    log.debug("сформировано заявлений: {}", counter);
                }
            }
            document.close();
            copy.close();
            sw.stop();
            log.debug("Сформировано {} PDF заявлений на выпуск карт за {} ", models.size(), sw.toString());
        } catch (DocumentException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void firstPage() throws IOException, DocumentException {
        putTextInRectangle(model.getFio(), "FIO");
        setFormCheckbox("SEX_M", model.isSexMale());
        setFormCheckbox("SEX_F", model.isSexFemale());
        setFormText("BIRTHDATE", prepareDate(model.getBirthDate()), baseFont, DEFAULT_FONT_SIZE);
    }

    private void secondPage() throws IOException, DocumentException {
        // if (model.getMobileBankInfo() != null) {
        // MobileBankInfo mobileBank = model.getMobileBankInfo();
        // boolean mb = false;
        // if (mobileBank.getTariff() != null &&
        // mobileBank.getTariff().isFull())
        // mb = true;
        // setFormCheckbox("MOBILE_BANK", mb);
        // }
        // setFormText("ACCOUNT_NUM",
        // model.getBankCardInfo().getDepositAccountNum(), baseFont,
        // DEFAULT_FONT_SIZE);
    }

    private void putIssuerAndSubdivision(String issuer, String subdiv) {
        Rectangle issuerBox = form.getFieldPositions("DOC_ISSUER").get(0).position;
        issuer = fixSpaces(issuer);
        String issuerString = "";
        if (!StringUtils.isEmpty(issuer) && !StringUtils.isEmpty(subdiv)) {
            issuerString = issuer + ", " + subdiv;
            // если не влезает в одну строку шрифтом по умолчанию
            if (getStringWidth(issuerString) > issuerBox.getWidth()) {
                // режем "кем выдан" пока не начнёт влезать в коробку
                // уменьшенным шрифтом
                while (!issuer.isEmpty()) {
                    issuerString = issuer + ", " + subdiv;
                    if (putTextInRectangle(issuerString, issuerBox, true) == ColumnText.NO_MORE_TEXT)
                        break;
                    issuer = issuer.substring(0, issuer.length() - 1);
                }
            }
        } else if (!StringUtils.isEmpty(issuer)) {
            issuerString = issuer;
        } else if (!StringUtils.isEmpty(subdiv)) {
            issuerString = subdiv;
        }
        putTextInRectangle(issuerString, issuerBox, false);
    }

    private String fixSpaces(String s) {
        if (s == null)
            return s;
        s = s.replaceAll("\\s+", " ");
        return s;
    }

    private int putTextInRectangle(String text, String fieldName) {
        Rectangle rectangle = form.getFieldPositions(fieldName).get(0).position;
        return putTextInRectangle(text, rectangle, false);
    }

    private int putTextInRectangle(String text, Rectangle box, boolean simulate) {
        if (StringUtils.isEmpty(text))
            return ColumnText.NO_MORE_TEXT;
        // if (!simulate) {
        // cb.endText();
        // cb.saveState();
        // cb.setColorStroke(com.itextpdf.text.BaseColor.RED);
        // cb.rectangle(box.x, box.y, box.x1 - box.x, box.y1 - box.y);
        // cb.stroke();
        // cb.restoreState();
        // cb.beginText();
        // cb.setFontAndSize(baseFont, DEFAULT_FONT_SIZE);
        // }
        if (getStringWidth(text) <= box.getRight() - box.getLeft()) {
            if (!simulate) {
                float fontHeight = baseFont.getFontDescriptor(BaseFont.ASCENT, DEFAULT_FONT_SIZE)
                        - baseFont.getFontDescriptor(BaseFont.DESCENT, DEFAULT_FONT_SIZE);
                cb.showTextAligned(PdfContentByte.ALIGN_LEFT, text, box.getLeft() + 2, box.getTop() - fontHeight, 0);
            }
            return ColumnText.NO_MORE_TEXT;
        } else {
            cb.saveState();
            try {
                ColumnText ct = new ColumnText(cb);
                Phrase phrase = new Phrase(text, new Font(baseFont, BOXED_TEXT_FONT_SIZE));
                ct.setSimpleColumn(phrase, box.getLeft() + 2, box.getTop() + 2, box.getRight(), box.getBottom(), 8.5f,
                        Element.ALIGN_LEFT);
                return ct.go(simulate);
            } catch (DocumentException e) {
                throw new RuntimeException(e);
            } finally {
                cb.restoreState();
            }
        }
    }

    private String prepareDate(Date date) {
        if (date == null)
            return "";
        return simpleDateFormat.format(date);
    }

    private void setFormCheckbox(String fieldName, boolean on) throws IOException, DocumentException {
        form.setField(fieldName, on ? "On" : "Off");
    }

    private void setFormText(String fieldName, String text, BaseFont font, Integer fontSize) throws IOException,
            DocumentException {
        if (text == null)
            return;
        if (font != null)
            form.setFieldProperty(fieldName, "textfont", font, null);
        if (fontSize != null)
            form.setFieldProperty(fieldName, "textsize", new Float(fontSize), null);
        form.setField(fieldName, text);
    }

    private static String preparePhone(String phoneNum) {
        if (phoneNum == null)
            return null;
        phoneNum = phoneNum.replaceAll("[^0-9]", "");
        phoneNum = StringUtils.leftPad(phoneNum, 10);
        if (phoneNum.length() > 10)
            phoneNum = phoneNum.substring(phoneNum.length() - 10);
        return phoneNum;
    }

    private void setFormPhone(String field1Name, String field2Name, String phone) throws IOException, DocumentException {
        if (phone == null)
            phone = "";
        String ph = preparePhone(phone);
        String ph1 = StringUtils.substring(ph, 0, 3);
        setFormText(field1Name, ph1, baseFont, DEFAULT_FONT_SIZE);
        String ph2 = StringUtils.substring(ph, 3);
        setFormText(field2Name, ph2, baseFont, DEFAULT_FONT_SIZE);
    }
}
