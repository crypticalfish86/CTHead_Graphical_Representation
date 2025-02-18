import java.io.*;
import java.util.HashMap;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.*;
import javafx.scene.control.Slider;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.PixelWriter;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
public class Main extends Application {
    public static final String CT_HEAD_FILEPATH = "CThead-256cubed.bin"; //filepath for 3D dataset
    public static final int DATA_SIZE = 256; //the constant width, height and depth of the dataset;
    short[][][] datasetVoxelByteValues; //A 3D dataset to store the byte values of the CT scan
    float[][][] datasetGreyValues; //A 3D dataset to store the normalised values(0-1) of the byte dataset
    int currentTopSlice = 128; //the current viewed slice of the CT scan for the top view
    int currentFrontSlice = 128; //the current viewed slice of the CT scan for the front view
    int currentSideSlice = 128; //the current viewed slice of the CT scan for the side view
    short min = Short.MAX_VALUE; //minimum byte value in dataset (used in normalisation, initialised at max value)
    short max = Short.MIN_VALUE; //maximum byte value in dataset (used in normalisation, initialised at min value)
    float opacityThreshold = 0.03f;
    float skinOpacity = 0.12f;

    /**
     * Reads data and places the values of each voxel in datasetVoxelByteValues
     * (Also reads max and min byte values in dataset in set up for normalisation to use in datasetGreyValues)
     */
    public void readDataset() throws IOException{
        File file = new File(CT_HEAD_FILEPATH);
        DataInputStream input = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));

        datasetVoxelByteValues = new short[DATA_SIZE][DATA_SIZE][DATA_SIZE];

        for (int slide = 0; slide < DATA_SIZE; slide++) {
            for (int row = 0; row < DATA_SIZE; row++) {
                for (int col = 0; col < DATA_SIZE; col++) {
                    int firstByte = ((int) input.readByte()) & 0xff; //read in first byte of voxel
                    int secondByte = ((int) input.readByte()) & 0xff; //read in second byte of voxel
                    short read = (short) ((secondByte << 8) | firstByte); //"Swizzle" the bytes (they're in wrong order for java)
                    datasetVoxelByteValues[slide][row][col] = read;

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
    }

    /**Set up a dataset containing normalised values (between 0-1) to create greyscale images of the data*/
    public void normaliseDatasetValues() {
        //normalise the byte values from the byte dataset and add to a new dataset
        datasetGreyValues = new float[DATA_SIZE][DATA_SIZE][DATA_SIZE];

        for (int z = 0; z < DATA_SIZE; z++) {
            for (int y = 0; y < DATA_SIZE; y++) {
                for (int x = 0; x < DATA_SIZE; x++) {
                    datasetGreyValues[z][y][x] = ((float) datasetVoxelByteValues[z][y][x] - (float) min) / ((float) max - (float) min);
                }
            }
        }
    }

    /**Update the sliced image that is passed into this function to the current slice*/
    public void updateImageSlice(WritableImage image, String imageKey) {

        PixelWriter newWriter = image.getPixelWriter();

        for (int y = 0; y < DATA_SIZE; y++) {
            for (int x = 0; x < DATA_SIZE; x++) {
                float val = switch (imageKey) {
                    case "Top" -> datasetGreyValues[currentTopSlice][y][x];
                    case "Front" -> datasetGreyValues[y][currentFrontSlice][x];
                    case "Side" -> datasetGreyValues[y][x][currentSideSlice];
                    default -> 0;

                };
                Color color = Color.color(val, val, val);
                newWriter.setColor(x, y, color);
            }

        }
    }

    /**Write the relevant MIP picture to the image*/
    public void writeMIP(WritableImage image, String imageKey) {
        PixelWriter newWriter = image.getPixelWriter();
        for (int y = 0; y < DATA_SIZE; y++) {
            for (int x = 0; x < DATA_SIZE; x++) {
                float val = getMaxFromRay(x, y ,imageKey);
                Color color = Color.color(val, val, val);
                newWriter.setColor(x, y, color);
            }
        }
    }
        /**Shoot a ray through the normalised dataset and return the maximum value found along that ray*/
        private float getMaxFromRay(int x, int y, String imageKey) {
            float currentMaximum = min;
            for (int slice = 0; slice < DATA_SIZE; slice++) {
                float voxelToCompare = switch (imageKey) {
                    case "Top" -> datasetGreyValues[slice][y][x];
                    case "Front" -> datasetGreyValues[y][slice][x];
                    case "Side" -> datasetGreyValues[y][x][slice];
                    default ->  0;
                };

                if (voxelToCompare > currentMaximum) {
                    currentMaximum = voxelToCompare;
                }
            }

            return currentMaximum;
        }

    public void updateVolumeRender(WritableImage image, String imageKey) {
            PixelWriter newWriter = image.getPixelWriter();

            for (int y = 0; y < DATA_SIZE; y++) {
                for (int x = 0; x < DATA_SIZE; x++) {
                    float accumOpacity = 1f;
                    float accumRed = 0f;
                    float accumGreen = 0f;
                    float accumBlue = 0f;
                    float lighting = 1f;//TODO when depth based shading implemented this changes

                    for (int slice = 0; slice < DATA_SIZE; slice++) {
                        if (accumOpacity < opacityThreshold) {
                            break;
                        }
                        short voxelToRead = switch (imageKey) {
                            case "Front" -> datasetVoxelByteValues[y][slice][x];
                            case "Side" -> datasetVoxelByteValues[y][x][slice];
                            default -> datasetVoxelByteValues[slice][y][x];
                        };


                        float voxelRed;
                        float voxelGreen;
                        float voxelBlue;
                        float voxelOpacity;

                        if (voxelToRead < -300) {
                            voxelRed = 0f;
                            voxelGreen = 0f;
                            voxelBlue = 0f;
                            voxelOpacity = 0f;
                        }
                        else if (voxelToRead < 50) {
                            voxelRed = 0.82f;
                            voxelGreen = 0.49f;
                            voxelBlue = 0.18f;
                            voxelOpacity = skinOpacity;
                        }
                        else if (voxelToRead < 300) {
                            voxelRed = 0f;
                            voxelGreen = 0f;
                            voxelBlue = 0f;
                            voxelOpacity = 0f;
                        }
                        else {
                            voxelRed = 1f;
                            voxelGreen = 1f;
                            voxelBlue = 1f;
                            voxelOpacity = 0.8f;
                        }

                        accumRed += accumOpacity * voxelOpacity * lighting * voxelRed;
                        accumGreen += accumOpacity * voxelOpacity * lighting * voxelGreen;
                        accumBlue += accumOpacity * voxelOpacity * lighting * voxelBlue;
                        accumOpacity *= (1 - voxelOpacity);
                    }
                    Color color = Color.color(Math.min(accumRed, 1f), Math.min(accumGreen, 1f), Math.min(accumBlue, 1f));
                    newWriter.setColor(x, y, color);
                }
            }
    }

    private float normaliseSkinOpacity(int value) {
            return (float) value / 100f;
    }

    /*Set up the stage for launch*/
    @Override
    public void start(Stage stage) {
        stage.setTitle("CS-256 Coursework");

        /*read in and set up 3D datasets*/
        try {
            readDataset();
            normaliseDatasetValues();
        } catch (IOException error) {
            System.out.println(error);
        }

        /*Initialise The images and write to them the default slices*/
        WritableImage topSlice = new WritableImage(DATA_SIZE, DATA_SIZE);
        updateImageSlice(topSlice, "Top");
        WritableImage frontSlice = new WritableImage(DATA_SIZE, DATA_SIZE);
        updateImageSlice(frontSlice, "Front");
        WritableImage sideSlice = new WritableImage(DATA_SIZE, DATA_SIZE);
        updateImageSlice(sideSlice, "Side");

        WritableImage topMIPImage = new WritableImage(DATA_SIZE, DATA_SIZE);
        writeMIP(topMIPImage, "Top");
        WritableImage frontMIPImage = new WritableImage(DATA_SIZE, DATA_SIZE);
        writeMIP(frontMIPImage, "Front");
        WritableImage sideMIPImage = new WritableImage(DATA_SIZE, DATA_SIZE);
        writeMIP(sideMIPImage, "Side");

        WritableImage topVolumeRender = new WritableImage(DATA_SIZE, DATA_SIZE);
        updateVolumeRender(topVolumeRender, "Top");
        WritableImage frontVolumeRender = new WritableImage(DATA_SIZE, DATA_SIZE);
        updateVolumeRender(frontVolumeRender, "Front");
        WritableImage sideVolumeRender = new WritableImage(DATA_SIZE, DATA_SIZE);
        updateVolumeRender(sideVolumeRender, "Side");

        /*Set all the writable images to imageviews for display on the stage*/
        ImageView topSliceView = new ImageView(topSlice);
        ImageView frontSliceView = new ImageView(frontSlice);
        ImageView sideSliceView = new ImageView(sideSlice);
        ImageView topMIPView = new ImageView(topMIPImage);
        ImageView frontMIPView = new ImageView(frontMIPImage);
        ImageView sideMIPView = new ImageView(sideMIPImage);
        ImageView topVolumeView = new ImageView(topVolumeRender);
        ImageView frontVolumeView = new ImageView(frontVolumeRender);
        ImageView sideVolumeView = new ImageView(sideVolumeRender);



        /*Initialize a new grid pane*/
        GridPane grid = new GridPane();
        grid.setHgap(1); // Horizontal gap in pixels between grid cells
        grid.setVgap(1); // Vertical gap in pixels between grid cells
        grid.setAlignment(Pos.TOP_CENTER);


        /*Set up a sliders with a listener that updates the image of the relevant picture to the slice value of the slider*/
        //topSliceSlider
        Slider topSliceSlider = new Slider(0, 255, currentTopSlice);
        topSliceSlider.setBlockIncrement(1);//ensure every single image is reachable by the slider
        topSliceSlider.setSnapToTicks(false); //ensure there is no snapping (smooth transition through images)
        topSliceSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
                currentTopSlice = newValue.intValue();
                updateImageSlice(topSlice, "Top");
        });

        //frontSliceSlider
        Slider frontSliceSlider = new Slider(0, 255, currentFrontSlice);
        frontSliceSlider.setBlockIncrement(1); //ensure every single image is reachable by the slider
        frontSliceSlider.setSnapToTicks(false); //ensure there is no snapping (smooth transition through images)
        frontSliceSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            currentFrontSlice = newValue.intValue();
            updateImageSlice(frontSlice, "Front");
        });

        //sideSliceSlider
        Slider sideSliceSlider = new Slider(0, 255, currentSideSlice);
        sideSliceSlider.setBlockIncrement(1); //ensure every single image is reachable by the slider
        sideSliceSlider.setSnapToTicks(false); //ensure there is no snapping (smooth transition through images)
        sideSliceSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            currentSideSlice = newValue.intValue();
            updateImageSlice(sideSlice, "Side");
        });


        //topVolumeSlider
        Slider topVolumeSlider = new Slider(0, 100, skinOpacity);
        topVolumeSlider.setBlockIncrement(1);
        topVolumeSlider.setSnapToTicks(false);
        topVolumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            skinOpacity = normaliseSkinOpacity(newValue.intValue());
            updateVolumeRender(topVolumeRender, "Top");
        });

        //frontVolumeSlider
        Slider frontVolumeSlider = new Slider(0, 100, skinOpacity);
        frontVolumeSlider.setBlockIncrement(1);
        frontVolumeSlider.setSnapToTicks(false);
        frontVolumeSlider.valueProperty().addListener((observable, oldValue, newValue) -> {
            skinOpacity = normaliseSkinOpacity(newValue.intValue());
            updateVolumeRender(frontVolumeRender, "Front");
        });

        //sideVolumeSlider
        Slider sideVolumeSlider = new Slider(0, 100, skinOpacity);
        sideVolumeSlider.setBlockIncrement(1);
        sideVolumeSlider.setSnapToTicks(false);
        sideVolumeSlider.valueProperty().addListener((observable, OldValue, newValue) -> {
            skinOpacity = normaliseSkinOpacity(newValue.intValue());
            updateVolumeRender(sideVolumeRender, "Side");
        });

        /*Set up vboxes and titles to display each view (top 3 are the slices, middle 3 are MIP, last 3 are volume renders)*/
        VBox topVboxSlice = new VBox(10);
        Label topLabel = new Label("Top View");
        topLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");
        topLabel.setMaxWidth(Double.MAX_VALUE);
        topLabel.setAlignment(Pos.CENTER);
        topVboxSlice.getChildren().addAll(topLabel, topSliceView, topSliceSlider);
        grid.add(topVboxSlice, 1, 1);

        VBox frontVboxSlice = new VBox(10);
        Label frontLabel = new Label("Front View");
        frontLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold");
        frontLabel.setMaxWidth(Double.MAX_VALUE);
        frontLabel.setAlignment(Pos.CENTER);
        frontVboxSlice.getChildren().addAll(frontLabel, frontSliceView, frontSliceSlider);
        grid.add(frontVboxSlice, 2, 1);

        VBox sideVboxSlice = new VBox(10);
        Label sideLabel = new Label("Side View");
        sideLabel.setStyle("-fx-font-size: 18px; -fx-font-weight: bold");
        sideLabel.setMaxWidth(Double.MAX_VALUE);
        sideLabel.setAlignment(Pos.CENTER);
        sideVboxSlice.getChildren().addAll(sideLabel, sideSliceView, sideSliceSlider);
        grid.add(sideVboxSlice, 3, 1);

        grid.add(topMIPView, 1, 2);
        grid.add(frontMIPView, 2, 2);
        grid.add(sideMIPView, 3, 2);

        VBox topVboxVolume = new VBox(10);
        topVboxVolume.getChildren().addAll(topVolumeView, topVolumeSlider);
        grid.add(topVboxVolume, 1, 3);

        VBox frontVboxVolume = new VBox(10);
        frontVboxVolume.getChildren().addAll(frontVolumeView, frontVolumeSlider);
        grid.add(frontVboxVolume, 2, 3);

        VBox sideVboxVolume = new VBox(10);
        sideVboxVolume.getChildren().addAll(sideVolumeView, sideVolumeSlider);
        grid.add(sideVboxVolume, 3, 3);


        /*Initialise and show a new scene on the stage with the grid added to it*/
        Scene scene = new Scene(grid, DATA_SIZE * 4, DATA_SIZE * 4);
        stage.setScene(scene); //set that scene onto the stage
        stage.show(); //show the stage
    }


    //launch stage
    public static void main(String[] args) {
        launch();
    }
}