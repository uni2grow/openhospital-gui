package org.isf.video.gui;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.jgoodies.binding.adapter.ComboBoxAdapter;
import com.jgoodies.binding.list.SelectionInList;
import com.jgoodies.forms.factories.CC;
import org.isf.utils.image.ImageUtil;
import org.isf.utils.jobjects.Cropping;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class PhotoboothComponentImpl extends PhotoboothComponent {
    private static final Logger LOGGER = LoggerFactory.getLogger(PhotoboothComponentImpl.class);
    private static final DefaultListCellRenderer RESOLUTION_DROPDOWN_OPTION_RENDERER = new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(final JList list,
                                                      final Object value,
                                                      final int index,
                                                      final boolean isSelected,
                                                      final boolean cellHasFocus) {
            final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value != null && value instanceof Dimension) {
                final Dimension valueAsDimension = (Dimension) value;
                setText(String.format("%d x %d", (int) valueAsDimension.getWidth(), (int) valueAsDimension.getHeight()));
            }
            return component;
        }
    };
    private static final DefaultListCellRenderer WEBCAM_DROPDOWN_OPTION_RENDERER = new DefaultListCellRenderer() {
        @Override
        public Component getListCellRendererComponent(final JList list,
                                                      final Object value,
                                                      final int index,
                                                      final boolean isSelected,
                                                      final boolean cellHasFocus) {
            final Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
            if (value != null && value instanceof Webcam) {
                final Webcam webcam = (Webcam) value;
                setText(webcam.getName());
            }
            return component;
        }
    };

    private Webcam webcam;
    private final List<Dimension> supportedResolutions;
    private WebcamPanel webcamPanel;
    private final PhotoboothPanelPresentationModel photoboothPanelPresentationModel;
    private final JDialog owner;
    private Cropping cropping;
    private Action okAction;
    private PropertyChangeListener webcamResolutionChangeListener;
    private final List<Webcam> allWebcams;
    private ComboBoxAdapter<Dimension> resolutionModel;
    private final SelectionInList<Dimension> dimensionSelectionInList;
    private PropertyChangeListener webcamChangeListener;


    public PhotoboothComponentImpl(final PhotoboothPanelPresentationModel model,
                                   final JDialog owner) {
        this.photoboothPanelPresentationModel = model;
        this.owner = owner;

        this.allWebcams = Webcam.getWebcams();
        this.webcam = Webcam.getDefault();

        this.supportedResolutions = new ArrayList<Dimension>();
        Collections.addAll(supportedResolutions, webcam.getDevice().getResolutions());
        dimensionSelectionInList = new SelectionInList<Dimension>(supportedResolutions);
    }

    @Override
    public void initComponents() {
        super.initComponents();

        // set initial size of all photo panels to use the resolution defined on the model
        getStreamingPanel().setPreferredSize(photoboothPanelPresentationModel.getResolution());
        getStreamingPanel().setMinimumSize(photoboothPanelPresentationModel.getResolution());
        getSnapshotPanel().setPreferredSize(photoboothPanelPresentationModel.getResolution());
        getSnapshotPanel().setMinimumSize(photoboothPanelPresentationModel.getResolution());
        this.resolutionComboBox.setRenderer(RESOLUTION_DROPDOWN_OPTION_RENDERER);
        this.webcamComboBox.setRenderer(WEBCAM_DROPDOWN_OPTION_RENDERER);

        this.webcam.setViewSize(photoboothPanelPresentationModel.getResolution());
        this.webcamPanel = new WebcamPanel(webcam, false);
        getStreamingPanel().add(webcamPanel, CC.xy(1, 1));
    }

    @Override
    protected void initGUIState() throws Exception {
        super.initGUIState();
        this.okAction.setEnabled(false);
        this.webcamPanel.start();
    }

    @Override
    protected void bind() throws Exception {
        super.bind();
        this.resolutionModel = new ComboBoxAdapter(
                dimensionSelectionInList,
                photoboothPanelPresentationModel.getModel(PhotoboothPanelModel.PROPERTY_RESOLUTION)
        );
        this.resolutionComboBox.setModel(resolutionModel);

        this.webcamComboBox.setModel(new ComboBoxAdapter(
                allWebcams,
                photoboothPanelPresentationModel.getModel(PhotoboothPanelModel.PROPERTY_WEBCAM)
        ));

        webcamResolutionChangeListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
                final Object newValue = propertyChangeEvent.getNewValue();
                if (newValue != null && newValue instanceof Dimension) {
                    PhotoboothComponentImpl.this.stopWebcam();

                    LOGGER.info("Changing webcam dimension to {}", newValue);
                    webcam.setViewSize((Dimension) newValue);
                    PhotoboothComponentImpl.this.getStreamingPanel().setPreferredSize((Dimension) newValue);
                    PhotoboothComponentImpl.this.getStreamingPanel().setMinimumSize((Dimension) newValue);
                    PhotoboothComponentImpl.this.getSnapshotPanel().setPreferredSize((Dimension) newValue);
                    PhotoboothComponentImpl.this.getSnapshotPanel().setMinimumSize((Dimension) newValue);

                    LOGGER.info("Closing webcam before attaching to panel.");
                    webcam.close();
                    webcamPanel = new WebcamPanel(webcam);
                    PhotoboothComponentImpl.this.getStreamingPanel().add(webcamPanel, CC.xy(1, 1));
                    owner.pack();
                    owner.repaint();
                    owner.validate();
                }
            }
        };
        photoboothPanelPresentationModel.addBeanPropertyChangeListener(PhotoboothPanelModel.PROPERTY_RESOLUTION, webcamResolutionChangeListener);

        webcamChangeListener = new PropertyChangeListener() {
            @Override
            public void propertyChange(PropertyChangeEvent propertyChangeEvent) {
                final Object newValue = propertyChangeEvent.getNewValue();
                if (newValue != null && newValue instanceof Webcam) {
                    PhotoboothComponentImpl.this.stopWebcam();

                    webcam = (Webcam) newValue;
                    LOGGER.info("Webcam changed to {}", webcam.getName());

                    supportedResolutions.clear();
                    final Dimension[] allResolutions = webcam.getDevice().getResolutions();
                    Collections.addAll(supportedResolutions, allResolutions);
                    dimensionSelectionInList.fireContentsChanged(0, supportedResolutions.size() - 1);

                    // set to highest resolution
                    photoboothPanelPresentationModel.setResolution(allResolutions[allResolutions.length - 1]);
                    LOGGER.info("Closing webcam {} before changing resolution", webcam.getName());
                    webcam.close();

                    LOGGER.info("Changing resolution to {}", photoboothPanelPresentationModel.getResolution());
                    webcam.setViewSize(photoboothPanelPresentationModel.getResolution());

                    PhotoboothComponentImpl.this.getStreamingPanel().setPreferredSize(photoboothPanelPresentationModel.getResolution());
                    PhotoboothComponentImpl.this.getStreamingPanel().setMinimumSize(photoboothPanelPresentationModel.getResolution());
                    PhotoboothComponentImpl.this.getSnapshotPanel().setPreferredSize(photoboothPanelPresentationModel.getResolution());
                    PhotoboothComponentImpl.this.getSnapshotPanel().setMinimumSize(photoboothPanelPresentationModel.getResolution());

                    LOGGER.info("Attaching webcam {} to panel", webcam.getName());
                    webcamPanel = new WebcamPanel(webcam);
                    PhotoboothComponentImpl.this.getStreamingPanel().add(webcamPanel, CC.xy(1, 1));
                    owner.pack();
                    owner.repaint();
                    owner.validate();

                }
            }
        };
        photoboothPanelPresentationModel.addBeanPropertyChangeListener(PhotoboothPanelModel.PROPERTY_WEBCAM, webcamChangeListener);
    }

    private void stopWebcam() {
        webcamPanel.stop();
        webcam.close();
        getStreamingPanel().remove(webcamPanel);
    }

    @Override
    protected PhotoboothPanelPresentationModel buildModel()  {
        return photoboothPanelPresentationModel;
    }

    @Override
    protected void initEventHandling() throws Exception {
        super.initEventHandling();

        this.okAction = new AbstractAction("OK") {
            @Override
            public void actionPerformed(final ActionEvent actionEvent) {
                // change image on the model, based on the cropped image
                presentationModel().setImage(cropping.clipImage());
                cleanup();
                owner.dispose();
            }
        };
        getOkButton().setAction(okAction);

        getCancelButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                PhotoboothComponentImpl.this.presentationModel().triggerFlush();
                PhotoboothComponentImpl.this.cleanup();
                owner.dispose();
            }
        });

        getCaptureButton().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent actionEvent) {
                final Dimension currentResolution = photoboothPanelPresentationModel.getResolution();
                // resize the image to match the current selected resolution. This is because under some circumstances, the
                // webcam's viewSize seems to be different from the currently selected resolution. Weird. i know..
                final BufferedImage resizedImage = ImageUtil.scaleImage(webcam.getImage(), (int) currentResolution.getWidth(), (int) currentResolution.getHeight());

                // set image on the cropping panel.
                cropping = new Cropping(resizedImage);
                okAction.setEnabled(true);
                SwingUtilities.invokeLater(new Runnable() {
                    @Override
                    public void run() {
                        PhotoboothComponentImpl.this.getSnapshotPanel().removeAll();
                        PhotoboothComponentImpl.this.getSnapshotPanel().add(cropping, CC.xy(1, 1));
                        PhotoboothComponentImpl.this.getPhotoBoothPanel().repaint();
                        PhotoboothComponentImpl.this.getPhotoBoothPanel().revalidate();
                    }
                });
            }
        });
    }

    public void cleanup() {
        stopWebcam();

        // need to remove listener here, to prevent memory leak the next time we open the photo frame again.
        photoboothPanelPresentationModel.removeBeanPropertyChangeListener(PhotoboothPanelModel.PROPERTY_RESOLUTION, webcamResolutionChangeListener);
        photoboothPanelPresentationModel.removeBeanPropertyChangeListener(PhotoboothPanelModel.PROPERTY_WEBCAM, webcamChangeListener);
    }
}
