import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.collections.*;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.*;
import javafx.scene.Scene;
import javafx.scene.chart.PieChart;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

public class TransportPassSystem extends Application {


    private static final String DB_URL = "jdbc:mysql://localhost:3306/transport_db";
    private static final String DB_USER = "Rahul9969";
    private static final String DB_PASSWORD = "Rahul@9969";

    private static final String TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS transport_pass (
                id INT AUTO_INCREMENT PRIMARY KEY,
                passenger_name VARCHAR(100) NOT NULL,
                pass_type VARCHAR(100) NOT NULL,
                duration_type VARCHAR(50) NOT NULL,
                duration_days INT NOT NULL,
                source VARCHAR(100),
                destination VARCHAR(100),
                valid_until DATE NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    private static final String USER_TABLE_SQL = """
            CREATE TABLE IF NOT EXISTS auth_user (
                id INT AUTO_INCREMENT PRIMARY KEY,
                username VARCHAR(80) NOT NULL UNIQUE,
                password_hash VARCHAR(128) NOT NULL,
                created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            )
            """;

    // --- UI state ---
    private final ObservableList<Pass> masterPasses = FXCollections.observableArrayList();
    private FilteredList<Pass> filteredPasses;
    private SortedList<Pass> sortedPasses;

    private TableView<Pass> table;
    private TextField passengerNameField;
    private ComboBox<String> typeSuggestions;
    private ComboBox<String> durationComboBox;
    private ComboBox<String> sourceComboBox;
    private ComboBox<String> destinationComboBox;
    private Label validUntilLabel;

    private TextField searchField;
    private ToggleButton showAllToggle;
    private ToggleButton activeToggle;
    private ToggleButton expiredToggle;

    private Label totalPassLabel;
    private Label activePassLabel;
    private Label expiringSoonLabel;
    private PieChart passTypeChart;
    private Label statusLabel;
    private Label userBadge;
    private Stage primaryStage;
    private String currentUser;

    private boolean suppressStatusAnimation = false;

    // --- entry point ---
    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        primaryStage = stage;
        ensureDriver();
        initDatabase();
        showAuthScreen(stage);
    }

    // --- scene management ---

    private void showAuthScreen(Stage stage) {
        Scene authScene = buildAuthScene(stage);
        stage.setTitle("Transport Pass System – Sign In");
        stage.setScene(authScene);
        stage.centerOnScreen();
        stage.show();
    }

    private Scene buildAuthScene(Stage stage) {
        Label title = new Label("Sustainable Transit Access");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold;");

        Label subtitle = new Label("Log in or create an account to manage smart passes.");
        subtitle.setStyle("-fx-text-fill: #546e7a;");

        TextField loginUser = new TextField();
        loginUser.setPromptText("Username");
        PasswordField loginPassword = new PasswordField();
        loginPassword.setPromptText("Password");
        Label loginStatus = new Label();
        loginStatus.setWrapText(true);

        Button loginButton = new Button("Sign In");
        loginButton.setDefaultButton(true);
        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setStyle("-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
        loginButton.setOnAction(e -> handleLogin(loginUser.getText(), loginPassword.getText(), loginStatus, stage));

        VBox loginCard = new VBox(10,
                new Label("Login"),
                loginUser,
                loginPassword,
                loginButton,
                loginStatus);
        loginCard.setPadding(new Insets(18));
        loginCard.setStyle("-fx-background-color: white; -fx-border-color: #dcdfe4; -fx-border-radius: 8; -fx-background-radius: 8;");
        loginCard.setPrefWidth(280);

        TextField registerUser = new TextField();
        registerUser.setPromptText("Choose a username");
        PasswordField registerPassword = new PasswordField();
        registerPassword.setPromptText("Choose a password");
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText("Confirm password");
        Label registerStatus = new Label();
        registerStatus.setWrapText(true);

        Button registerButton = new Button("Create Account");
        registerButton.setMaxWidth(Double.MAX_VALUE);
        registerButton.setStyle("-fx-background-color: #1565c0; -fx-text-fill: white; -fx-font-weight: bold;");
        registerButton.setOnAction(e -> handleRegistration(registerUser.getText(), registerPassword.getText(), confirmPassword.getText(), registerStatus));

        VBox registerCard = new VBox(10,
                new Label("Register"),
                registerUser,
                registerPassword,
                confirmPassword,
                registerButton,
                registerStatus);
        registerCard.setPadding(new Insets(18));
        registerCard.setStyle("-fx-background-color: white; -fx-border-color: #dcdfe4; -fx-border-radius: 8; -fx-background-radius: 8;");
        registerCard.setPrefWidth(280);

        HBox cards = new HBox(20, loginCard, registerCard);
        cards.setAlignment(Pos.CENTER);

        BorderPane root = new BorderPane();
        VBox header = new VBox(8, title, subtitle);
        header.setAlignment(Pos.CENTER);
        header.setPadding(new Insets(20, 0, 10, 0));
        root.setTop(header);
        root.setCenter(cards);
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: linear-gradient(#f5f7fa, #e6ecf5);");

        return new Scene(root, 700, 420);
    }

    private void showMainScene(Stage stage) {
        BorderPane root = new BorderPane();
        root.setTop(buildTopBar(stage));
        root.setCenter(buildMainLayout());
        root.setBottom(buildStatusBar());

        Scene mainScene = new Scene(root, 1200, 700);
        stage.setTitle("Transport Pass System – Sustainable Transit (SDG 11)");
        stage.setScene(mainScene);
        stage.centerOnScreen();
        stage.show();

        loadPasses();
        if (table != null) {
            table.requestFocus();
        }
        updateSummary();
        showStatus("Welcome, " + (currentUser != null ? currentUser : "Guest") + "!");
    }

    private void handleLogin(String username, String password, Label feedback, Stage stage) {
        String normalizedUser = normalizeUsername(username);
        if (normalizedUser.isEmpty() || password == null || password.isBlank()) {
            feedback.setTextFill(Color.web("#c62828"));
            feedback.setText("Enter both username and password.");
            return;
        }
        String sql = "SELECT password_hash FROM auth_user WHERE username=?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedUser);
            ResultSet rs = ps.executeQuery();
            if (rs.next()) {
                String storedHash = rs.getString("password_hash");
                if (storedHash.equals(hashPassword(password))) {
                    currentUser = normalizedUser;
                    feedback.setTextFill(Color.web("#2e7d32"));
                    feedback.setText("Login successful. Loading workspace...");
                    Platform.runLater(() -> showMainScene(stage));
                } else {
                    feedback.setTextFill(Color.web("#c62828"));
                    feedback.setText("Incorrect password. Try again.");
                }
            } else {
                feedback.setTextFill(Color.web("#c62828"));
                feedback.setText("No account found for that username.");
            }
        } catch (SQLException ex) {
            feedback.setTextFill(Color.web("#c62828"));
            feedback.setText("Could not sign in. Please check database connection.");
        }
    }

    private void handleRegistration(String username, String password, String confirmPassword, Label feedback) {
        String normalizedUser = normalizeUsername(username);
        if (normalizedUser.length() < 3) {
            feedback.setTextFill(Color.web("#c62828"));
            feedback.setText("Username must be at least 3 characters.");
            return;
        }
        if (password == null || password.length() < 6) {
            feedback.setTextFill(Color.web("#c62828"));
            feedback.setText("Password must be at least 6 characters.");
            return;
        }
        if (!password.equals(confirmPassword)) {
            feedback.setTextFill(Color.web("#c62828"));
            feedback.setText("Passwords do not match.");
            return;
        }

        String sql = "INSERT INTO auth_user (username, password_hash) VALUES (?, ?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, normalizedUser);
            ps.setString(2, hashPassword(password));
            ps.executeUpdate();
            feedback.setTextFill(Color.web("#2e7d32"));
            feedback.setText("Account created. You can now log in.");
        } catch (SQLIntegrityConstraintViolationException duplicate) {
            feedback.setTextFill(Color.web("#c62828"));
            feedback.setText("That username is already taken.");
        } catch (SQLException ex) {
            feedback.setTextFill(Color.web("#c62828"));
            feedback.setText("Registration failed. Please try again later.");
        }
    }

    private void logout(Stage stage) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Log out from the Transport Pass System?", ButtonType.YES, ButtonType.NO);
        confirm.setTitle("Confirm Logout");
        confirm.setHeaderText(null);
        confirm.initOwner(stage);
        confirm.showAndWait()
                .filter(btn -> btn == ButtonType.YES)
                .ifPresent(btn -> {
                    currentUser = null;
                    masterPasses.clear();
                    if (table != null) {
                        table.getItems().clear();
                    }
                    showAuthScreen(stage);
                });
    }

    private String normalizeUsername(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ENGLISH);
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : hashed) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not supported", e);
        }
    }

    private void updateUserBadge() {
        if (userBadge != null) {
            String user = currentUser == null ? "Guest" : currentUser;
            userBadge.setText("Signed in as " + user);
        }
    }

    // --- UI construction helpers ---

    private VBox buildMainLayout() {
        table = buildTable();
        filteredPasses = new FilteredList<>(masterPasses, pass -> true);
        sortedPasses = new SortedList<>(filteredPasses);
        sortedPasses.comparatorProperty().bind(table.comparatorProperty());
        table.setItems(sortedPasses);

        VBox left = new VBox(12, buildSearchPanel(), table);
        VBox.setVgrow(table, Priority.ALWAYS);
        left.setPadding(new Insets(12, 0, 12, 12));

        VBox passForm = buildPassForm();
        VBox dashboardPanel = buildDashboardPanel();
        VBox right = new VBox(18, passForm, dashboardPanel);
        right.setPadding(new Insets(12, 12, 12, 12));
        right.setPrefWidth(380);
        right.setMinWidth(380);
        VBox.setVgrow(dashboardPanel, Priority.ALWAYS);

        ScrollPane rightScroller = new ScrollPane(right);
        rightScroller.setFitToWidth(true);
        rightScroller.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        rightScroller.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        rightScroller.setPrefViewportWidth(400);
        rightScroller.setStyle("-fx-background-color: transparent;");

        HBox main = new HBox(18, left, new Separator(Orientation.VERTICAL), rightScroller);
        HBox.setHgrow(left, Priority.ALWAYS);
        return new VBox(main);
    }

    private TableView<Pass> buildTable() {
        TableView<Pass> tv = new TableView<>();
        tv.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_ALL_COLUMNS);
        tv.setPlaceholder(new Label("No passes yet. Use the form to create one."));

        TableColumn<Pass, Number> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(cell -> new SimpleIntegerProperty(0)); // Placeholder, actual value from cell factory
        idCol.setCellFactory(col -> new TableCell<Pass, Number>() {
            @Override
            protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || getTableRow() == null) {
                    setText(null);
                } else {
                    int rowIndex = getTableRow().getIndex();
                    setText(String.valueOf(rowIndex + 1));
                }
            }
        });
        idCol.setPrefWidth(60);

        TableColumn<Pass, String> nameCol = new TableColumn<>("Passenger Name");
        nameCol.setCellValueFactory(new PropertyValueFactory<>("passengerName"));

        TableColumn<Pass, String> typeCol = new TableColumn<>("Pass Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("passType"));

        TableColumn<Pass, String> durCol = new TableColumn<>("Duration");
        durCol.setCellValueFactory(new PropertyValueFactory<>("durationType"));

        TableColumn<Pass, String> sourceCol = new TableColumn<>("Source");
        sourceCol.setCellValueFactory(new PropertyValueFactory<>("source"));

        TableColumn<Pass, String> destCol = new TableColumn<>("Destination");
        destCol.setCellValueFactory(new PropertyValueFactory<>("destination"));

        TableColumn<Pass, String> validCol = new TableColumn<>("Valid Until");
        validCol.setCellValueFactory(new PropertyValueFactory<>("validUntil"));
        validCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String value, boolean empty) {
                super.updateItem(value, empty);
                if (empty || value == null) {
                    setText(null);
                    setTextFill(Color.BLACK);
                    setStyle("");
                } else {
                    LocalDate date = LocalDate.parse(value);
                    setText(value);
                    long days = ChronoUnit.DAYS.between(LocalDate.now(), date);
                    if (days < 0) {
                        setTextFill(Color.RED);
                    } else if (days <= 5) {
                        setTextFill(Color.web("#d98300"));
                    } else {
                        setTextFill(Color.web("#1f7a1f"));
                    }
                }
            }
        });

        TableColumn<Pass, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(cell ->
                new SimpleStringProperty(cell.getValue().computeStatus()));
        statusCol.setPrefWidth(120);

        tv.getColumns().addAll(idCol, nameCol, typeCol, durCol, sourceCol, destCol, validCol, statusCol);
        tv.getSelectionModel().selectedItemProperty()
                .addListener((obs, old, sel) -> populateForm(sel));
        tv.setRowFactory(tableView -> {
            TableRow<Pass> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2 && !row.isEmpty()) {
                    populateForm(row.getItem());
                }
            });
            return row;
        });
        return tv;
    }

    private VBox buildPassForm() {
        Label header = new Label("Pass Management");
        header.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

        passengerNameField = new TextField();
        passengerNameField.setPromptText("e.g. John Doe");

        typeSuggestions = new ComboBox<>();
        typeSuggestions.getItems().addAll(
                "Bus",
                "Metro",
                "Train");
        typeSuggestions.setEditable(false);
        typeSuggestions.setPromptText("Select pass type");

        durationComboBox = new ComboBox<>();
        durationComboBox.getItems().addAll(
                "Daily",
                "Weekly",
                "Monthly",
                "Quarterly",
                "Yearly");
        durationComboBox.setEditable(false);
        durationComboBox.setPromptText("Select duration");
        durationComboBox.valueProperty().addListener((obs, old, val) -> calculateValidity());

        // Mumbai transport locations
        sourceComboBox = new ComboBox<>();
        sourceComboBox.getItems().addAll(
                "Andheri",
                "Bandra",
                "Borivali",
                "Churchgate",
                "Colaba",
                "Dadar",
                "Goregaon",
                "Juhu",
                "Kurla",
                "Marine Lines",
                "Matunga",
                "Parel",
                "Vashi",
                "Versova",
                "Worli",
                "Mumbai Central",
                "Bandra Kurla Complex",
                "Chembur",
                "Ghatkopar",
                "Kalyan",
                "Thane",
                "Nerul",
                "Belapur",
                "Panvel",
                "Other");
        sourceComboBox.setEditable(true);
        sourceComboBox.setPromptText("Select or enter source");

        destinationComboBox = new ComboBox<>();
        destinationComboBox.getItems().addAll(
                "Andheri",
                "Bandra",
                "Borivali",
                "Churchgate",
                "Colaba",
                "Dadar",
                "Goregaon",
                "Juhu",
                "Kurla",
                "Marine Lines",
                "Matunga",
                "Parel",
                "Vashi",
                "Versova",
                "Worli",
                "Mumbai Central",
                "Bandra Kurla Complex",
                "Chembur",
                "Ghatkopar",
                "Kalyan",
                "Thane",
                "Nerul",
                "Belapur",
                "Panvel",
                "Other");
        destinationComboBox.setEditable(true);
        destinationComboBox.setPromptText("Select or enter destination");

        validUntilLabel = new Label("Validity will be calculated");
        validUntilLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
        validUntilLabel.setWrapText(true);

        Button addBtn = buildActionButton("Add Pass", "#2e7d32", event -> addPass());
        Button updateBtn = buildActionButton("Update Selected", "#1565c0", event -> updatePass());
        Button deleteBtn = buildActionButton("Delete Selected", "#b71c1c", event -> deletePass());
        Button resetBtn = buildActionButton("Reset Form", "#546e7a", event -> clearForm());

        addBtn.setDefaultButton(true);
        deleteBtn.setDisable(true);
        updateBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());
        deleteBtn.disableProperty().bind(table.getSelectionModel().selectedItemProperty().isNull());

        ColumnConstraints cc1 = new ColumnConstraints();
        cc1.setPercentWidth(50);
        ColumnConstraints cc2 = new ColumnConstraints();
        cc2.setPercentWidth(50);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(12);
        grid.add(new Label("Passenger Name:"), 0, 0);
        grid.add(passengerNameField, 0, 1, 2, 1);
        grid.add(new Label("Pass Type:"), 0, 2);
        grid.add(typeSuggestions, 0, 3, 2, 1);
        grid.add(new Label("Duration:"), 0, 4);
        grid.add(durationComboBox, 0, 5, 2, 1);
        grid.add(new Label("Source:"), 0, 6);
        grid.add(sourceComboBox, 0, 7, 2, 1);
        grid.add(new Label("Destination:"), 0, 8);
        grid.add(destinationComboBox, 0, 9, 2, 1);
        grid.add(new Label("Valid Until:"), 0, 10);
        grid.add(validUntilLabel, 0, 11, 2, 1);
        grid.getColumnConstraints().addAll(cc1, cc2);

        HBox buttonsTop = new HBox(10, addBtn, updateBtn);
        HBox buttonsBottom = new HBox(10, deleteBtn, resetBtn);
        buttonsTop.setAlignment(Pos.CENTER);
        buttonsBottom.setAlignment(Pos.CENTER);

        VBox container = new VBox(12, header, grid, buttonsTop, buttonsBottom);
        container.setPadding(new Insets(14));
        container.setStyle("-fx-background-color: linear-gradient(#ffffff, #f2f4f7); -fx-background-radius: 12; -fx-border-radius: 12; -fx-border-color: #dcdfe4;");
        return container;
    }

    private Button buildActionButton(String text, String color, javafx.event.EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button(text);
        button.setMaxWidth(Double.MAX_VALUE);
        button.setOnAction(handler);
        button.setStyle("-fx-background-color: " + color + "; -fx-text-fill: white; -fx-font-weight: bold;");
        HBox.setHgrow(button, Priority.ALWAYS);
        return button;
    }

private VBox buildDashboardPanel() {
    Label header = new Label("Insights & Trends");
    header.setStyle("-fx-font-size: 18px; -fx-font-weight: bold;");

    totalPassLabel = buildMetricLabel("Total Passes", "0");
    activePassLabel = buildMetricLabel("Active Passes", "0");
    expiringSoonLabel = buildMetricLabel("Expiring in ≤5 days", "0");

    HBox statRow = new HBox(10,
            buildMetricCard(totalPassLabel, "#1565c0"),
            buildMetricCard(activePassLabel, "#2e7d32"));
    HBox statRow2 = new HBox(10,
            buildMetricCard(expiringSoonLabel, "#d84315"));
    statRow.setAlignment(Pos.CENTER);
    statRow2.setAlignment(Pos.CENTER_LEFT);

    passTypeChart = new PieChart();
    passTypeChart.setTitle("Pass Type Distribution");
    passTypeChart.setLegendVisible(true);
    passTypeChart.setLabelsVisible(true);
    passTypeChart.setStartAngle(90);

    // ✅ Ensure it’s always visible
    passTypeChart.setPrefSize(350, 260);
    passTypeChart.setMinSize(300, 240);
    VBox.setVgrow(passTypeChart, Priority.ALWAYS);

    Button refreshBtn = new Button("Refresh Dashboard");
    refreshBtn.setOnAction(e -> refreshDashboard());
    refreshBtn.setStyle("-fx-background-color: #37474f; -fx-text-fill: white; -fx-font-weight: bold;");

    // ✅ Wrap the chart inside a ScrollPane to ensure it's shown even when resized
    ScrollPane chartContainer = new ScrollPane(passTypeChart);
    chartContainer.setFitToWidth(true);
    chartContainer.setPrefViewportHeight(280);
    chartContainer.setStyle("-fx-background-color: transparent;");

    VBox card = new VBox(14, header, statRow, statRow2, chartContainer, refreshBtn);
    card.setPadding(new Insets(14));
    card.setStyle("-fx-background-color: linear-gradient(#ffffff, #eef2f7); -fx-background-radius: 12; "
            + "-fx-border-radius: 12; -fx-border-color: #dcdfe4;");
    card.setPrefHeight(500);
    card.setMinHeight(480);

    return card;
}


    private Label buildMetricLabel(String title, String value) {
        Label label = new Label(value);
        label.setStyle("-fx-font-size: 26px; -fx-font-weight: bold;");
        Tooltip tooltip = new Tooltip(title);
        label.setTooltip(tooltip);
        return label;
    }

    private VBox buildMetricCard(Label valueLabel, String color) {
        String title = valueLabel.getTooltip() != null ? valueLabel.getTooltip().getText() : "";
        Label name = new Label(title);
        name.setStyle("-fx-text-fill: rgba(255,255,255,0.85); -fx-font-weight: 600;");
        valueLabel.setTextFill(Color.WHITE);

        VBox box = new VBox(6, name, valueLabel);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(12));
        box.setPrefWidth(150);
        box.setStyle("-fx-background-color: " + color + "; -fx-background-radius: 10;");
        return box;
    }

    private VBox buildSearchPanel() {
        Label title = new Label("Pass Directory");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");

        searchField = new TextField();
        searchField.setPromptText("Search by name, type or status...");
        searchField.textProperty().addListener((obs, old, val) -> applyFilters());
        searchField.setOnKeyPressed(evt -> {
            if (evt.getCode() == KeyCode.ENTER) {
                applyFilters();
            }
        });

        ToggleGroup filterGroup = new ToggleGroup();
        showAllToggle = new ToggleButton("All");
        showAllToggle.setToggleGroup(filterGroup);
        showAllToggle.setSelected(true);
        activeToggle = new ToggleButton("Active");
        activeToggle.setToggleGroup(filterGroup);
        expiredToggle = new ToggleButton("Expired");
        expiredToggle.setToggleGroup(filterGroup);

        filterGroup.selectedToggleProperty().addListener((obs, old, val) -> applyFilters());
        HBox filters = new HBox(8, showAllToggle, activeToggle, expiredToggle);
        filters.setAlignment(Pos.CENTER_LEFT);

        Button clearFilters = new Button("Clear Filters");
        clearFilters.setOnAction(e -> {
            searchField.clear();
            showAllToggle.setSelected(true);
            table.getSelectionModel().clearSelection();
        });

        VBox box = new VBox(8, title, searchField, filters, clearFilters);
        box.setPadding(new Insets(0, 12, 12, 0));
        return box;
    }

    private HBox buildTopBar(Stage stage) {
        MenuBar menuBar = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem refreshItem = new MenuItem("Reload From Database");
        refreshItem.setOnAction(e -> loadPasses());
        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> stage.close());
        fileMenu.getItems().addAll(refreshItem, new SeparatorMenuItem(), exitItem);

        Menu helpMenu = new Menu("Help");
        MenuItem aboutItem = new MenuItem("About");
        aboutItem.setOnAction(e -> showInfo("""
                Sustainable transit management supporting SDG 11.
                Track passes with live analytics and expiry alerts.
                """));
        helpMenu.getItems().addAll(aboutItem);

        menuBar.getMenus().addAll(fileMenu, helpMenu);

        userBadge = new Label();
        userBadge.setStyle("-fx-text-fill: #37474f; -fx-font-weight: bold;");
        updateUserBadge();

        Button logoutBtn = new Button("Logout");
        logoutBtn.setOnAction(e -> logout(stage));
        logoutBtn.setStyle("-fx-background-color: #c62828; -fx-text-fill: white; -fx-font-weight: bold;");

        HBox userBox = new HBox(10, userBadge, logoutBtn);
        userBox.setAlignment(Pos.CENTER_RIGHT);

        BorderPane menuRow = new BorderPane();
        menuRow.setLeft(menuBar);
        menuRow.setRight(userBox);
        menuRow.setPadding(new Insets(0, 8, 0, 0));

        Label banner = new Label(" Sustainable Cities – Inclusive Mobility ");
        banner.setStyle("-fx-text-fill: white; -fx-font-size: 15px; -fx-font-weight: bold;");

        HBox bannerBox = new HBox(banner);
        bannerBox.setAlignment(Pos.CENTER);
        bannerBox.setPadding(new Insets(6));
        bannerBox.setStyle("-fx-background-color: linear-gradient(to right, #1e88e5, #43a047);");

        Tooltip.install(banner, new Tooltip("SDG 11: Sustainable Cities & Communities"));

        VBox top = new VBox(menuRow, bannerBox);
        return new HBox(top);
    }


    private HBox buildStatusBar() {
        statusLabel = new Label("Ready.");
        statusLabel.setPadding(new Insets(4, 0, 4, 8));

        ProgressIndicator progressIndicator = new ProgressIndicator();
        progressIndicator.setPrefSize(28, 28);
        progressIndicator.visibleProperty().bind(Bindings.isEmpty(masterPasses));

        HBox bar = new HBox(10, statusLabel, new Region(), progressIndicator);
        HBox.setHgrow(bar.getChildren().get(1), Priority.ALWAYS);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(6, 12, 6, 12));
        bar.setStyle("-fx-background-color: #f8f9fb; -fx-border-color: #d7dce3; -fx-border-width: 1 0 0 0;");
        return bar;
    }

    // --- filtering + analytics ---
    private void applyFilters() {
        filteredPasses.setPredicate(pass -> {
            String query = Optional.ofNullable(searchField.getText()).orElse("").trim().toLowerCase(Locale.ENGLISH);
            boolean matchesSearch = query.isBlank()
                    || pass.getPassengerName().toLowerCase(Locale.ENGLISH).contains(query)
                    || pass.getPassType().toLowerCase(Locale.ENGLISH).contains(query)
                    || pass.computeStatus().toLowerCase(Locale.ENGLISH).contains(query);

            Toggle selected = ((ToggleButton) showAllToggle).getToggleGroup().getSelectedToggle();
            boolean matchesToggle;
            if (selected == activeToggle) {
                matchesToggle = pass.isActive();
            } else if (selected == expiredToggle) {
                matchesToggle = !pass.isActive();
            } else {
                matchesToggle = true;
            }
            return matchesSearch && matchesToggle;
        });
        updateSummary();
        showStatus("Applied filters.");
    }

    private void refreshDashboard() {
        updateSummary();
        showStatus("Dashboard refreshed.");
    }

private void updateSummary() {
    totalPassLabel.setText(String.valueOf(masterPasses.size()));
    long activeCount = masterPasses.stream().filter(Pass::isActive).count();
    activePassLabel.setText(String.valueOf(activeCount));
    long expiringSoon = masterPasses.stream()
            .filter(pass -> pass.daysUntilExpiry() >= 0 && pass.daysUntilExpiry() <= 5)
            .count();
    expiringSoonLabel.setText(String.valueOf(expiringSoon));

    Map<String, Long> countByType = masterPasses.stream()
            .collect(Collectors.groupingBy(Pass::getPassType, Collectors.counting()));

    Platform.runLater(() -> {
        passTypeChart.getData().clear();
        if (countByType.isEmpty()) {
            passTypeChart.getData().add(new PieChart.Data("No passes yet", 1));
        } else {
            countByType.forEach((type, count) ->
                    passTypeChart.getData().add(new PieChart.Data(type, count))
            );
        }
        passTypeChart.setVisible(true);
        passTypeChart.requestLayout();
    });
}


    // --- CRUD operations ---
    private void addPass() {
        Optional<Pass> candidate = readForm();
        if (candidate.isEmpty()) return;

        Pass pass = candidate.get();
        String sql = "INSERT INTO transport_pass (passenger_name, pass_type, duration_type, duration_days, source, destination, valid_until) VALUES (?,?,?,?,?,?,?)";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {

            ps.setString(1, pass.getPassengerName());
            ps.setString(2, pass.getPassType());
            ps.setString(3, pass.getDurationType());
            ps.setInt(4, pass.getDurationDays());
            ps.setString(5, pass.getSource());
            ps.setString(6, pass.getDestination());
            ps.setDate(7, java.sql.Date.valueOf(LocalDate.parse(pass.getValidUntil())));
            ps.executeUpdate();

            ResultSet rs = ps.getGeneratedKeys();
            if (rs.next()) pass.setId(rs.getInt(1));

            masterPasses.add(pass);
            table.getSelectionModel().select(pass);
            clearForm();
            updateSummary();
            showStatus("Pass added successfully.");
        } catch (SQLException ex) {
            showError("Could not add pass", ex);
        }
    }

    private void updatePass() {
        Pass selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Select a pass to update.");
            return;
        }
        Optional<Pass> candidate = readForm();
        if (candidate.isEmpty()) return;

        Pass pass = candidate.get();
        String sql = "UPDATE transport_pass SET passenger_name=?, pass_type=?, duration_type=?, duration_days=?, source=?, destination=?, valid_until=? WHERE id=?";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, pass.getPassengerName());
            ps.setString(2, pass.getPassType());
            ps.setString(3, pass.getDurationType());
            ps.setInt(4, pass.getDurationDays());
            ps.setString(5, pass.getSource());
            ps.setString(6, pass.getDestination());
            ps.setDate(7, java.sql.Date.valueOf(LocalDate.parse(pass.getValidUntil())));
            ps.setInt(8, selected.getId());
            ps.executeUpdate();

            selected.setPassengerName(pass.getPassengerName());
            selected.setPassType(pass.getPassType());
            selected.setDurationType(pass.getDurationType());
            selected.setDurationDays(pass.getDurationDays());
            selected.setSource(pass.getSource());
            selected.setDestination(pass.getDestination());
            selected.setValidUntil(pass.getValidUntil());
            table.refresh();
            updateSummary();
            clearForm();
            showStatus("Pass updated.");
        } catch (SQLException ex) {
            showError("Could not update pass", ex);
        }
    }

    private void deletePass() {
        Pass selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showInfo("Select a pass to delete.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete selected pass?", ButtonType.OK, ButtonType.CANCEL);
        confirm.setHeaderText("Confirm delete");
        confirm.initOwner(table.getScene().getWindow());
        confirm.showAndWait().filter(btn -> btn == ButtonType.OK).ifPresent(btn -> {
            String sql = "DELETE FROM transport_pass WHERE id=?";
            try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
                 PreparedStatement ps = conn.prepareStatement(sql)) {

                ps.setInt(1, selected.getId());
                ps.executeUpdate();
                masterPasses.remove(selected);
                clearForm();
                updateSummary();
                showStatus("Pass removed.");
            } catch (SQLException ex) {
                showError("Could not delete pass", ex);
            }
        });
    }

    // --- form helpers ---
    private int getDurationDays(String durationType) {
        if (durationType == null) return 0;
        return switch (durationType.toLowerCase()) {
            case "daily" -> 1;
            case "weekly" -> 7;
            case "monthly" -> 30;
            case "quarterly" -> 90;
            case "yearly" -> 365;
            default -> 0;
        };
    }

    private void calculateValidity() {
        String durationType = durationComboBox.getValue();
        if (durationType != null && !durationType.isEmpty()) {
            int days = getDurationDays(durationType);
            if (days > 0) {
                LocalDate validDate = LocalDate.now().plusDays(days);
                validUntilLabel.setText("Valid until: " + validDate.toString());
                validUntilLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32;");
            } else {
                validUntilLabel.setText("Select duration to calculate validity");
                validUntilLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
            }
        } else {
            validUntilLabel.setText("Validity will be calculated");
            validUntilLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
        }
    }

    private Optional<Pass> readForm() {
        String passengerName = passengerNameField.getText().trim();
        String type = typeSuggestions.getValue();
        String durationType = durationComboBox.getValue();
        // Handle editable ComboBoxes - get value or editor text
        String source = sourceComboBox.getValue() != null && !sourceComboBox.getValue().isEmpty() 
                ? sourceComboBox.getValue().trim() 
                : (sourceComboBox.getEditor().getText() != null ? sourceComboBox.getEditor().getText().trim() : "");
        String destination = destinationComboBox.getValue() != null && !destinationComboBox.getValue().isEmpty()
                ? destinationComboBox.getValue().trim()
                : (destinationComboBox.getEditor().getText() != null ? destinationComboBox.getEditor().getText().trim() : "");

        if (passengerName.isEmpty()) {
            showInfo("Passenger name cannot be empty.");
            return Optional.empty();
        }
        if (type == null || type.isEmpty()) {
            showInfo("Please select a pass type.");
            return Optional.empty();
        }
        if (durationType == null || durationType.isEmpty()) {
            showInfo("Please select a duration.");
            return Optional.empty();
        }
        if (source.isEmpty()) {
            showInfo("Please select or enter a source.");
            return Optional.empty();
        }
        if (destination.isEmpty()) {
            showInfo("Please select or enter a destination.");
            return Optional.empty();
        }

        int durationDays = getDurationDays(durationType);
        LocalDate validDate = LocalDate.now().plusDays(durationDays);
        
        // Combine pass type and duration for display
        String fullPassType = type + " " + durationType;
        
        return Optional.of(new Pass(0, passengerName, fullPassType, durationType, durationDays, source, destination, validDate.toString()));
    }

    private void populateForm(Pass pass) {
        if (pass == null) {
            clearForm();
            return;
        }
        passengerNameField.setText(pass.getPassengerName());
        
        // Extract pass type (e.g., "Bus Monthly" -> "Bus")
        String passType = pass.getPassType();
        String baseType = passType;
        if (passType != null && passType.contains(" ")) {
            baseType = passType.substring(0, passType.lastIndexOf(" "));
        }
        if (typeSuggestions.getItems().contains(baseType)) {
            typeSuggestions.setValue(baseType);
        } else {
            typeSuggestions.getSelectionModel().clearSelection();
        }
        
        // Set duration type
        String durationType = pass.getDurationType();
        if (durationComboBox.getItems().contains(durationType)) {
            durationComboBox.setValue(durationType);
        } else {
            durationComboBox.getSelectionModel().clearSelection();
        }
        
        // Set source and destination
        if (pass.getSource() != null && !pass.getSource().isEmpty()) {
            sourceComboBox.setValue(pass.getSource());
            // If it's not in the list and ComboBox is editable, add it to the editor
            if (!sourceComboBox.getItems().contains(pass.getSource())) {
                sourceComboBox.getEditor().setText(pass.getSource());
            }
        }
        
        if (pass.getDestination() != null && !pass.getDestination().isEmpty()) {
            destinationComboBox.setValue(pass.getDestination());
            // If it's not in the list and ComboBox is editable, add it to the editor
            if (!destinationComboBox.getItems().contains(pass.getDestination())) {
                destinationComboBox.getEditor().setText(pass.getDestination());
            }
        }
        
        validUntilLabel.setText("Valid until: " + pass.getValidUntil());
        validUntilLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32;");
    }

    private void clearForm() {
        table.getSelectionModel().clearSelection();
        passengerNameField.clear();
        typeSuggestions.getSelectionModel().clearSelection();
        durationComboBox.getSelectionModel().clearSelection();
        sourceComboBox.getSelectionModel().clearSelection();
        sourceComboBox.getEditor().clear();
        destinationComboBox.getSelectionModel().clearSelection();
        destinationComboBox.getEditor().clear();
        validUntilLabel.setText("Validity will be calculated");
        validUntilLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #666;");
    }

    // --- database utilities ---
    private void ensureDriver() {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            showError("MySQL driver missing. Ensure mysql-connector-j is on classpath.", ex);
            Platform.exit();
        }
    }

    private void initDatabase() {
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement()) {
            stmt.executeUpdate(TABLE_SQL);
            stmt.executeUpdate(USER_TABLE_SQL);
            // Add new columns if they don't exist (for existing databases)
            try {
                stmt.executeUpdate("ALTER TABLE transport_pass ADD COLUMN passenger_name VARCHAR(100) NOT NULL DEFAULT 'Unknown'");
            } catch (SQLException e) {
                // Column already exists, ignore
            }
            try {
                stmt.executeUpdate("ALTER TABLE transport_pass ADD COLUMN duration_type VARCHAR(50)");
            } catch (SQLException e) {
                // Column already exists, ignore
            }
            try {
                stmt.executeUpdate("ALTER TABLE transport_pass ADD COLUMN source VARCHAR(100)");
            } catch (SQLException e) {
                // Column already exists, ignore
            }
            try {
                stmt.executeUpdate("ALTER TABLE transport_pass ADD COLUMN destination VARCHAR(100)");
            } catch (SQLException e) {
                // Column already exists, ignore
            }
            showStatus("Database ready.");
        } catch (SQLException ex) {
            showError("Failed to prepare database.", ex);
        }
    }

    private void loadPasses() {
        masterPasses.clear();
        String sql = "SELECT id, passenger_name, pass_type, duration_type, duration_days, source, destination, valid_until FROM transport_pass ORDER BY id";
        try (Connection conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASSWORD);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {

            while (rs.next()) {
                String durationType = rs.getString("duration_type");
                if (durationType == null || durationType.isEmpty()) {
                    // For backward compatibility, try to infer from duration_days
                    int days = rs.getInt("duration_days");
                    if (days == 1) durationType = "Daily";
                    else if (days == 7) durationType = "Weekly";
                    else if (days == 30) durationType = "Monthly";
                    else if (days == 90) durationType = "Quarterly";
                    else if (days == 365) durationType = "Yearly";
                    else durationType = "Monthly"; // default
                }
                masterPasses.add(new Pass(
                        rs.getInt("id"),
                        rs.getString("passenger_name"),
                        rs.getString("pass_type") != null ? rs.getString("pass_type") : "Unknown",
                        durationType,
                        rs.getInt("duration_days"),
                        rs.getString("source") != null ? rs.getString("source") : "",
                        rs.getString("destination") != null ? rs.getString("destination") : "",
                        rs.getDate("valid_until").toString()
                ));
            }
            if (filteredPasses != null) {
                applyFilters();
            }
            showStatus("Loaded " + masterPasses.size() + " passes.");
        } catch (SQLException ex) {
            showError("Could not load passes.", ex);
        }
    }

    // --- dialogs + status ---
    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, ButtonType.OK);
        alert.setHeaderText(null);
        alert.initOwner(table != null ? table.getScene().getWindow() : null);
        alert.showAndWait();
    }

    private void showError(String title, Exception ex) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(title);
        alert.setContentText(ex.getMessage());
        alert.showAndWait();
        ex.printStackTrace();
        showStatus(title + ": " + ex.getMessage());
    }

    private void showStatus(String message) {
        if (statusLabel == null) return;
        statusLabel.setText(message);
        if (!suppressStatusAnimation) {
            statusLabel.setStyle("-fx-text-fill: #1b5e20;");
            new Thread(() -> {
                try {
                    Thread.sleep(3200);
                } catch (InterruptedException ignored) {}
                Platform.runLater(() -> statusLabel.setStyle("-fx-text-fill: #263238;"));
            }).start();
        }
    }

    // --- data model ---
    public static class Pass {
        private final IntegerProperty id = new SimpleIntegerProperty();
        private final StringProperty passengerName = new SimpleStringProperty();
        private final StringProperty passType = new SimpleStringProperty();
        private final StringProperty durationType = new SimpleStringProperty();
        private final IntegerProperty durationDays = new SimpleIntegerProperty();
        private final StringProperty source = new SimpleStringProperty();
        private final StringProperty destination = new SimpleStringProperty();
        private final StringProperty validUntil = new SimpleStringProperty();

        public Pass(int id, String passengerName, String passType, String durationType, int durationDays, String source, String destination, String validUntil) {
            setId(id);
            setPassengerName(passengerName);
            setPassType(passType);
            setDurationType(durationType);
            setDurationDays(durationDays);
            setSource(source);
            setDestination(destination);
            setValidUntil(validUntil);
        }

        public int getId() { return id.get(); }
        public void setId(int value) { id.set(value); }
        public IntegerProperty idProperty() { return id; }

        public String getPassengerName() { return passengerName.get(); }
        public void setPassengerName(String value) { passengerName.set(value); }
        public StringProperty passengerNameProperty() { return passengerName; }

        public String getPassType() { return passType.get(); }
        public void setPassType(String value) { passType.set(value); }
        public StringProperty passTypeProperty() { return passType; }

        public String getDurationType() { return durationType.get(); }
        public void setDurationType(String value) { durationType.set(value); }
        public StringProperty durationTypeProperty() { return durationType; }

        public int getDurationDays() { return durationDays.get(); }
        public void setDurationDays(int value) { durationDays.set(value); }
        public IntegerProperty durationDaysProperty() { return durationDays; }

        public String getSource() { return source.get(); }
        public void setSource(String value) { source.set(value); }
        public StringProperty sourceProperty() { return source; }

        public String getDestination() { return destination.get(); }
        public void setDestination(String value) { destination.set(value); }
        public StringProperty destinationProperty() { return destination; }

        public String getValidUntil() { return validUntil.get(); }
        public void setValidUntil(String value) { validUntil.set(value); }
        public StringProperty validUntilProperty() { return validUntil; }

        public boolean isActive() {
            return LocalDate.parse(getValidUntil()).isAfter(LocalDate.now().minusDays(1));
        }

        public long daysUntilExpiry() {
            return ChronoUnit.DAYS.between(LocalDate.now(), LocalDate.parse(getValidUntil()));
        }

        public String computeStatus() {
            long days = daysUntilExpiry();
            if (days < 0) {
                return "Expired (" + (-days) + " days ago)";
            } else if (days == 0) {
                return "Expires today";
            } else if (days <= 5) {
                return "Expiring in " + days + " day" + (days == 1 ? "" : "s");
            } else {
                return "Active (" + days + " days left)";
            }
        }
    }
}