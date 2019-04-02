package common;

import com.sun.jna.ptr.PointerByReference;
import net.sourceforge.lept4j.*;
import net.sourceforge.lept4j.util.LeptUtils;
import org.apache.commons.io.FilenameUtils;

import java.awt.*;
import java.io.File;

public class ImageTools {

    public static Pix loadImage(File image) {
        Pix pix = Leptonica.INSTANCE.pixRead(image.getPath());

        return pix;
    }

    public static File saveImage(String savePath, Pix pix) {
        Leptonica.INSTANCE.pixWrite(savePath, pix, ILeptonica.IFF_TIFF);
        return new File(savePath);
    }

    public static Pix cropImage(Pix pix, Rectangle rect) {
        Box box = getLeptonicaBox(rect);
        PointerByReference pboxc = null;
        Pix pix2 = Leptonica1.pixClipRectangle(pix, box, pboxc);

        PointerByReference ptr = new PointerByReference();
        ptr.setValue(box.getPointer());
        Leptonica.INSTANCE.boxDestroy(ptr);

        return pix2;
    }

    public static void disposePixs(Pix... pixs) {
        for (Pix pix : pixs) {
            LeptUtils.disposePix(pix);
        }
    }

    public static File cropAndBinarizeImage(File image, Rectangle rect) {
        Pix pix = loadImage(image);
        Pix pix2 = cropImage(pix, rect);
        Pix pix3 = convertImageToGrayscale(pix2);
        Pix pix4 = binarizeImage(pix3);

        String croppedImagePath = getCroppedBinarizedImagePath(image, rect);
        File saved = saveImage(croppedImagePath, pix4);

        disposePixs(pix, pix2, pix3, pix4);

        return saved;
    }

    public static Box getLeptonicaBox(Rectangle rect) {
        int x = (int)rect.getX();
        int y = (int)rect.getY();
        int w = (int)rect.getWidth();
        int h = (int)rect.getHeight();
        Box box = Leptonica.INSTANCE.boxCreate(x, y, w, h);

        return box;
    }

    private static String getCroppedBinarizedImagePath(File image, Rectangle rect) {
        String baseName = FilenameUtils.getBaseName(image.getPath());
        String croppedName = getCroppedImageName(baseName, rect);
        String croppedBinarizedName = getBinarizedImageName(croppedName);
        String croppedBinarizedImagePath = getChildImagePath(image, croppedBinarizedName);
        return croppedBinarizedImagePath;
    }

    private static String getBinarizedImagePath(File image) {
        String baseName = FilenameUtils.getBaseName(image.getPath());
        String binName = getBinarizedImageName(baseName);
        String binImagePath = getChildImagePath(image, binName);
        return binImagePath;
    }

    private static String getChildImagePath(File parentImage, String childImageName) {
        String childImagePath = parentImage.getParent() + "\\" + childImageName + "." + FilenameUtils.getExtension(parentImage.getPath());
        return childImagePath;
    }

    private static String getCroppedImageName(String baseName, Rectangle rect) {
        int x = (int)rect.getX();
        int y = (int)rect.getY();
        int w = (int)rect.getWidth();
        int h = (int)rect.getHeight();
        String name = baseName + "_" + x + "_" + y + "_" + w + "_" + h;
        return name;
    }

    private static String getBinarizedImageName(String baseName) {
        String name = baseName + "_bin";
        return name;
    }

    public static Pix convertImageToGrayscale(Pix pix) {
        Pix pix1 = Leptonica.INSTANCE.pixConvertRGBToGrayFast(pix);

        return pix1;
    }

    public static File binarizeImage(File image) {
        Pix pix = loadImage(image);
        Pix pix1 = convertImageToGrayscale(pix);
        Pix pix2 = binarizeImage(pix1);

        String binarizedImagePath = getBinarizedImagePath(image);
        File saved = saveImage(binarizedImagePath, pix2);

        disposePixs(pix, pix1, pix2);

        return saved;
    }

    public static Pix binarizeImage(Pix pix) {
        int width = Leptonica.INSTANCE.pixGetWidth(pix);
        int height = Leptonica.INSTANCE.pixGetHeight(pix);
        int sx = width / 6;
        int sy = height / 6;
        int smoothx = width / 3;
        int smoothy = height / 3;
        float scorefract = 0.1f;

        PointerByReference ppixth = new PointerByReference();
        PointerByReference ppixd = new PointerByReference();
        Leptonica.INSTANCE.pixOtsuAdaptiveThreshold(pix, sx, sy, smoothx, smoothy, scorefract, ppixth, ppixd);
        Pix pix2 = new Pix(ppixd.getValue());

        return pix2;
    }

    public static void removeLinesAndBinarize(File image) {
//		for (int deg = 0; deg <= 180; deg+=10) {
//			Pix pix = leptInstance.pixRead(image.getPath());
//			Pix pixGray = leptInstance.pixConvertRGBToGrayFast(pix);
//			if (pixGray == null) {
//				pixGray = pix;
//			}
//			//int width = leptInstance.pixGetWidth(pixGray);
//			//int height = leptInstance.pixGetHeight(pixGray);
//
//			//leptInstance.box
//
//			float rad = (float)Math.toRadians(deg);
//			Pix pixRot = leptInstance.pixRotate(pixGray, rad, Leptonica.L_ROTATE_AREA_MAP, Leptonica.L_BRING_IN_WHITE, 0, 0);
//			Pix pixRem = LeptUtils.removeLinesAndBinarize(pixRot);
//			Pix pixCor = leptInstance.pixRotate(pixRem, -rad, Leptonica.L_ROTATE_AREA_MAP, Leptonica.L_BRING_IN_WHITE, 0, 0);
//			//leptInstance.pixDeskew(pixCor, )
//
//
//
//			String correctedImagePath = image.getParent() + "\\" + FilenameUtils.getBaseName(image.getPath()) + "_cor." + FilenameUtils.getExtension(image.getPath());
//			leptInstance.pixWrite(correctedImagePath, pixCor, ILeptonica.IFF_TIFF);
//			LeptUtils.disposePix(pixRot);
//			//LeptUtils.disposePix(pixRem);
//			LeptUtils.disposePix(pixCor);
//		}
        //leptInstance.pixRotate180(pixR, pixR);
        //pixR = leptInstance.pixThresholdToBinary(pixR, 170);
        //leptInstance.pixWrite(image.getPath(), pixR, ILeptonica.IFF_TIFF);



        Pix pix = Leptonica.INSTANCE.pixRead(image.getPath());
        Pix pix1 = Leptonica.INSTANCE.pixConvertRGBToGrayFast(pix);
        Pix pix2 = LeptUtils.removeLines(pix1);
        Pix pix3 = Leptonica.INSTANCE.pixRotate90(pix2, 1);
        Pix pix4 = LeptUtils.removeLines(pix3);
        Pix pix5 = Leptonica.INSTANCE.pixRotate90(pix4, -1);
        Pix pix6 = binarizeImage(pix5);
        Leptonica.INSTANCE.pixWrite(image.getPath(), pix6, ILeptonica.IFF_TIFF);

        LeptUtils.disposePix(pix);
        LeptUtils.disposePix(pix1);
        LeptUtils.disposePix(pix2);
        LeptUtils.disposePix(pix3);
        LeptUtils.disposePix(pix4);
        LeptUtils.disposePix(pix5);
        LeptUtils.disposePix(pix6);
    }

    public static Pix rotateImage(Pix pix, int deg, Dimension size) {
        float rad = (float)Math.toRadians(deg);
        Pix pixRot = Leptonica.INSTANCE.pixRotate(pix, rad, Leptonica.L_ROTATE_SHEAR, Leptonica.L_BRING_IN_WHITE, (int)size.getWidth(), (int)size.getHeight());

        return pixRot;
    }
}
