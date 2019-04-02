package textextraction;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

public class ProcessedPage {
    private String pageText;
    private int pageNum;
    private PageType pageType;
    private PageState pageState;
    private File pageFile;

    final static Logger logger = LogManager.getLogger(ProcessedPage.class);

    public enum PageType {
        PlainText, Schematic
    }
    public enum PageState {
        Normal, OCR, Error
    }

    public void cleanup() {
        if (pageFile != null) {
            logger.info("After processing page " + pageNum + ", now deleting file: " + pageFile.getName());
            pageFile.delete();
        }
    }

    public String getPageText() {
        return pageText;
    }

    public void setPageText(String pageText) {
        this.pageText = pageText;
    }

    public int getPageNum() {
        return pageNum;
    }

    public void setPageNum(int pageNum) {
        this.pageNum = pageNum;
    }

    public PageType getPageType() {
        return pageType;
    }

    public void setPageType(PageType pageType) {
        this.pageType = pageType;
    }

    public PageState getPageState() {
        return pageState;
    }

    public void setPageState(PageState pageState) {
        this.pageState = pageState;
    }

    public File getPageFile() {
        return pageFile;
    }

    public void setPageFile(File pageFile) {
        this.pageFile = pageFile;
    }
}
