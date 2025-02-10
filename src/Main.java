import java.io.*;
import java.util.InputMismatchException;

import javafx.application.Application;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.scene.Scene;
import javafx.scene.layout.*;
import javafx.scene.control.Slider;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.shape.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
public class Main extends Application {
    public final static String CT_HEAD_FILEPATH = "CThead-256cubed.bin"; //filepath for 3D dataset

    short[][][] datasetVoxelByteValuesTop; //byte values of 3D dataset from top view
    float[][][] datasetGreyValueTop; //normalised grey value for top dataset used in coloring images
    VBox topVbox; //the top view of the CThead
    int currentTopSlice = 128; //the current viewed slice of the CT scan for the top view

    short[][][] datasetVoxelByteValuesFront; //byte values of 3D dataset from front view
    float[][][] datasetGreyValueFront; //normalised grey value for front dataset used in coloring images
    VBox frontVbox;
    int currentFrontSlice = 128; //the current viewed slice of the CT scan for the front view

    short[][][] datasetVoxelByteValuesSide; //byte values of 3D dataset from side view
    float[][][] datasetGreyValueSide; //normalised grey value for side dataset used in coloring images
    VBox sideVbox;
    int currentSideSlice = 128; //the current viewed slice of the CT scan for the side view

    public final static int IMAGE_WIDTH = 256; //the width of view in the dataset
    public final static int IMAGE_HEIGHT = 256; //the height of every view in the dataset
    public final static int IMAGE_DEPTH = 256; //the depth of every view in the dataset

    short min = Short.MAX_VALUE; //minimum byte value in dataset (used in normalisation, initialised at max value)
    short max = Short.MIN_VALUE; //maximum byte value in dataset (used in normalisation, initialised at min value)

    /**
     * Reads data and places the values of each voxel in datasetVoxelByteValues
     */
    public void readDataset() throws IOException{
        File file = new File(CT_HEAD_FILEPATH);
        DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));

        datasetVoxelByteValuesTop = new short[IMAGE_DEPTH][IMAGE_HEIGHT][IMAGE_WIDTH]; //initialize 3D array for 3D dataset

        for (int slide = 0; slide < IMAGE_DEPTH; slide++) {
            for (int row = 0; row < IMAGE_HEIGHT; row++) {
                for (int col = 0; col < IMAGE_WIDTH; col++) {
                    int firstByte = ((int) input.readByte()) & 0xff; //read in first byte of voxel
                    int secondByte = ((int) input.readByte()) & 0xff; //read in second byte of voxel
                    short read = (short) ((secondByte << 8) | firstByte); //"Swizzle" the bytes (they're in wrong order for java)
                    datasetVoxelByteValuesTop[slide][row][col] = read; //assign the voxel value to that position

                    //if the byte is larger than the current maximum value found or lower than the minimum assign a new max/min
                    if (read > max) {
                        max = read;
                    }
                    if (read < min) {
                        min = read;
                    }
                }
            }
        }

        normaliseDatasetValues(); //for the top only (currently) normalise the values to intensities for use in grey
    }
        public void normaliseDatasetValues() {
            //normalise the byte values from the top dataset and add to a new dataset
            datasetGreyValueTop = new float[IMAGE_DEPTH][IMAGE_HEIGHT][IMAGE_WIDTH];
            for (int z = 0; z < IMAGE_DEPTH; z++) {
                for (int y = 0; y < IMAGE_HEIGHT; y++) {
                    for (int x = 0; x < IMAGE_WIDTH; x++) {
                        datasetGreyValueTop[z][y][x] =  ((float) datasetVoxelByteValuesTop[z][y][x] - (float) min) / ((float) max - (float) min);
                    }
                }
            }

            //used the normalised top dataset to fill the values in the front dataset (z,x,y -> y,z,x)
            datasetGreyValueFront = new float[IMAGE_HEIGHT][IMAGE_DEPTH][IMAGE_WIDTH];
            for (int y = 0; y < IMAGE_HEIGHT; y++) {
                for (int z = 0; z < IMAGE_DEPTH; z++) {
                    for (int x = 0; x < IMAGE_WIDTH; x++) {
                        datasetGreyValueFront[y][z][x] = datasetGreyValueTop[z][y][x];
                    }
                }
            }

            //use normalised top dataset to fill the values in the side dataset (z,x,y -> x,z,y)
            datasetGreyValueSide = new float[IMAGE_WIDTH][IMAGE_DEPTH][IMAGE_HEIGHT];
            for (int x = 0; x < IMAGE_WIDTH; x++) {
                for (int z = 0; z < IMAGE_DEPTH; z++) {
                    for (int y = 0; y < IMAGE_HEIGHT; y++) {
                        datasetGreyValueSide[x][z][y] = datasetGreyValueTop[z][y][x];
                    }
                }
            }
        }

    /**Write a new image for the sliced view of the CT scan head (the side being dependent on what string you pass in)*/
    public WritableImage writeNewImage(String imageToWrite){
        WritableImage newImage = new WritableImage(IMAGE_WIDTH, IMAGE_HEIGHT);
        PixelWriter newWriter = newImage.getPixelWriter();

        float[][][] dataset;
        int slice;

        if (imageToWrite.equals("Top")) {
            dataset = datasetGreyValueTop;
            slice = currentTopSlice;
        }
        else if (imageToWrite.equals("Front")) {
            dataset = datasetGreyValueFront;
            slice = currentFrontSlice;
        }
        else {
            dataset = datasetGreyValueSide;
            slice = currentSideSlice;
        }

        /*Write pixel by pixel the normalised values of the CT scan*/
        for (int y = 0; y < IMAGE_HEIGHT; y++) {
            for (int x = 0; x < IMAGE_WIDTH; x++) {
                float value = dataset[slice][y][x];
                Color color = Color.color(value, value, value); //this value needs to be between 0-1 as that's how colors are assigned in java
                newWriter.setColor(x, y, color);
            }
        }

        return newImage;
    }



    //set up stage
    @Override
    public void start(Stage stage) {
        stage.setTitle("CS-256 Coursework"); //The name of the window

        try {
            readDataset();
        } catch (IOException error) {
            System.out.println(error);
        }

        /**Initalize a new grid pane*/
        GridPane grid = new GridPane(); //A newly initialised pane to display on the stage
        grid.setHgap(1); // Horizontal gap between cells
        grid.setVgap(1); // Vertical gap between cells


        /**Wrap your writable image in an "ImageView" so you can display it on a grid (needs to be a node)*/
        ImageView topWritableImageView = new ImageView(writeNewImage("Top"));
        ImageView frontWritableImageView = new ImageView(writeNewImage("Front"));
        ImageView sideWritableImageView = new ImageView(writeNewImage("Side"));

        /**Set up a sliders with a listener that changes the image of the relevant picture*/
        //topSlider
        Slider topSlider = new Slider(0, 255, currentTopSlice);
        topSlider.setBlockIncrement(1);//ensure every single image is reachable by the slider
        topSlider.setSnapToTicks(false); //ensure there is no snapping (smooth transition through images)
        topSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                currentTopSlice = newValue.intValue();
                topWritableImageView.setImage(writeNewImage("Top"));
        });

        //frontSlider
        Slider frontSlider = new Slider(0, 255, currentFrontSlice);
        frontSlider.setBlockIncrement(1);
        frontSlider.setSnapToTicks(false);
        frontSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            currentFrontSlice = newValue.intValue();
            frontWritableImageView.setImage(writeNewImage("Front"));
        });

        //sideSlider
        Slider sideSlider = new Slider(0, 255, currentSideSlice);
        sideSlider.setBlockIncrement(1);
        sideSlider.setSnapToTicks(false);
        sideSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            currentSideSlice = newValue.intValue();
            sideWritableImageView.setImage(writeNewImage("Side"));
        });



        /**Set up vboxes to display each view*/
        topVbox = new VBox(10); //10 vertical pixels between images in vbox
        topVbox.getChildren().addAll(topWritableImageView, topSlider); //add both the top writable image and slider to vbox
        grid.add(topVbox, 1, 1);

        frontVbox = new VBox(10);
        frontVbox.getChildren().addAll(frontWritableImageView, frontSlider);
        grid.add(frontVbox, 2, 1);

        sideVbox = new VBox(10);
        sideVbox.getChildren().addAll(sideWritableImageView, sideSlider);
        grid.add(sideVbox, 3, 1);


        Scene scene = new Scene(grid, IMAGE_WIDTH * 4, IMAGE_HEIGHT * 4); //scene(currentpane, xpixelsWide, ypixelsWide)
        stage.setScene(scene); //set that scene onto the stage
        stage.show(); //show the stage
    }


    //launch stage
    public static void main(String[] args) {
        launch();
    }
}