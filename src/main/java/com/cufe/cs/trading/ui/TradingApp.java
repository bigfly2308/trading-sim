package com.cufe.cs.trading.ui;

import com.cufe.cs.trading.agent.HumanBroker;
import com.cufe.cs.trading.agent.TradingAgent;
import com.cufe.cs.trading.core.Account;
import com.cufe.cs.trading.core.MarketData;
import com.cufe.cs.trading.core.MatchingEngine;
import com.cufe.cs.trading.core.OrderBook;
import com.cufe.cs.trading.core.OrderRejectedException;
import com.cufe.cs.trading.io.ConfigLoader;
import com.cufe.cs.trading.model.PriceLevel;
import com.cufe.cs.trading.model.OrderType;
import com.cufe.cs.trading.model.Side;
import com.cufe.cs.trading.model.Trade;
import com.cufe.cs.trading.simulation.Simulation;

import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.beans.property.ReadOnlyDoubleWrapper;
import javafx.beans.property.ReadOnlyIntegerWrapper;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.SplitPane;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** JavaFX 图形界面：下单、深度、账户盈亏与价格走势的实时视图。 */
public class TradingApp extends Application {
    private Simulation simulation;
    private MatchingEngine engine;
    private Map<String, String> agentTypes = new HashMap<>();

    private ComboBox<String> symbolBox;
    private ComboBox<String> humanBox;
    private ToggleGroup sideGroup;
    private RadioButton buyRadio;
    private RadioButton sellRadio;
    private TextField priceField;
    private TextField qtyField;
    private CheckBox marketBox;
    private Label statusLabel;
    private Label runLabel;

    private TableView<DepthRow> depthTable;
    private TableView<AccountRow> accountTable;
    private final XYChart.Series<Number, Number> priceSeries = new XYChart.Series<>();

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage stage) {
        simulation = new Simulation(new ConfigLoader());
        engine = simulation.getEngine();
        for (TradingAgent agent : simulation.getAgents()) {
            agentTypes.put(agent.getAgentId(), agent.getType());
        }

        BorderPane root = new BorderPane();
        root.setTop(buildToolbar());
        root.setLeft(buildOrderForm());
        root.setCenter(buildDepthAndChart());
        root.setRight(buildAccountTable());

        Scene scene = new Scene(root, 1100, 700);
        stage.setTitle("多智能体连续竞价交易模拟系统");
        stage.setScene(scene);
        stage.show();

        Timeline timeline = new Timeline(new KeyFrame(Duration.millis(500), e -> refresh()));
        timeline.setCycleCount(Timeline.INDEFINITE);
        timeline.play();
    }

    private HBox buildToolbar() {
        Button toggle = new Button("开始模拟");
        toggle.setOnAction(e -> {
            if (simulation.isRunning()) {
                simulation.stop();
                toggle.setText("开始模拟");
                runLabel.setText("状态：已暂停");
            } else {
                simulation.start();
                toggle.setText("暂停模拟");
                runLabel.setText("状态：运行中");
            }
        });

        runLabel = new Label("状态：未开始");
        String llm = simulation.isLlmConfigured() ? "已配置" : "未配置（使用内置噪声策略）";
        Label llmLabel = new Label("LLM：" + llm);

        HBox box = new HBox(12, toggle, runLabel, llmLabel);
        box.setPadding(new Insets(10));
        return box;
    }

    private VBox buildOrderForm() {
        Label title = new Label("人工下单");
        title.setStyle("-fx-font-weight: bold;");

        symbolBox = new ComboBox<>();
        symbolBox.getItems().setAll(simulation.getMarketData().priceSnapshot().keySet());
        symbolBox.getSelectionModel().selectFirst();

        humanBox = new ComboBox<>();
        for (TradingAgent agent : simulation.getAgents()) {
            if (agent instanceof HumanBroker) {
                humanBox.getItems().add(agent.getAgentId());
            }
        }
        humanBox.getSelectionModel().selectFirst();

        buyRadio = new RadioButton("买入");
        sellRadio = new RadioButton("卖出");
        buyRadio.setSelected(true);
        sideGroup = new ToggleGroup();
        buyRadio.setToggleGroup(sideGroup);
        sellRadio.setToggleGroup(sideGroup);

        priceField = new TextField();
        priceField.setPromptText("价格");
        qtyField = new TextField();
        qtyField.setPromptText("数量");
        marketBox = new CheckBox("市价单");

        Button submit = new Button("提交订单");
        submit.setOnAction(e -> submitOrder());

        statusLabel = new Label();
        statusLabel.setWrapText(true);
        statusLabel.setMaxWidth(220);

        VBox box = new VBox(8,
                title,
                new Label("股票："), symbolBox,
                new Label("交易账户："), humanBox,
                new HBox(8, buyRadio, sellRadio),
                priceField, qtyField, marketBox,
                submit, statusLabel);
        box.setPadding(new Insets(10));
        return box;
    }

    private SplitPane buildDepthAndChart() {
        depthTable = new TableView<>();
        TableColumn<DepthRow, String> sideCol = new TableColumn<>("方向");
        sideCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getSide()));
        TableColumn<DepthRow, Number> priceCol = new TableColumn<>("价格");
        priceCol.setCellValueFactory(c -> new ReadOnlyDoubleWrapper(c.getValue().getPrice()));
        TableColumn<DepthRow, Number> qtyCol = new TableColumn<>("数量");
        qtyCol.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().getQty()));
        depthTable.getColumns().addAll(sideCol, priceCol, qtyCol);
        depthTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("成交序号");
        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("价格");
        LineChart<Number, Number> priceChart = new LineChart<>(xAxis, yAxis);
        priceChart.setTitle("价格走势");
        priceChart.setAnimated(false);
        priceChart.getData().add(priceSeries);

        VBox depthPane = new VBox(6, new Label("盘口（前5档）"), depthTable);
        depthPane.setPadding(new Insets(10));
        VBox chartPane = new VBox(6, priceChart);
        chartPane.setPadding(new Insets(10));

        SplitPane split = new SplitPane(depthPane, chartPane);
        split.setDividerPositions(0.45);
        return split;
    }

    private VBox buildAccountTable() {
        accountTable = new TableView<>();
        TableColumn<AccountRow, String> agentCol = new TableColumn<>("智能体");
        agentCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getAgent()));
        TableColumn<AccountRow, String> typeCol = new TableColumn<>("类型");
        typeCol.setCellValueFactory(c -> new ReadOnlyStringWrapper(c.getValue().getType()));
        TableColumn<AccountRow, Number> cashCol = new TableColumn<>("现金");
        cashCol.setCellValueFactory(c -> new ReadOnlyDoubleWrapper(c.getValue().getCash()));
        TableColumn<AccountRow, Number> sharesCol = new TableColumn<>("持仓");
        sharesCol.setCellValueFactory(c -> new ReadOnlyIntegerWrapper(c.getValue().getShares()));
        TableColumn<AccountRow, Number> equityCol = new TableColumn<>("总资产");
        equityCol.setCellValueFactory(c -> new ReadOnlyDoubleWrapper(c.getValue().getEquity()));
        TableColumn<AccountRow, Number> retCol = new TableColumn<>("收益率%");
        retCol.setCellValueFactory(c -> new ReadOnlyDoubleWrapper(c.getValue().getReturnPct()));
        accountTable.getColumns().addAll(agentCol, typeCol, cashCol, sharesCol, equityCol, retCol);
        accountTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        VBox box = new VBox(6, new Label("账户与盈亏"), accountTable);
        box.setPadding(new Insets(10));
        box.setPrefWidth(480);
        return box;
    }

    private void submitOrder() {
        String symbol = symbolBox.getValue();
        String human = humanBox.getValue();
        Side side = buyRadio.isSelected() ? Side.BUY : Side.SELL;
        OrderType type = marketBox.isSelected() ? OrderType.MARKET : OrderType.LIMIT;

        double price = 0;
        int quantity = 0;
        try {
            if (type == OrderType.LIMIT) {
                price = Double.parseDouble(priceField.getText().trim());
            }
            quantity = Integer.parseInt(qtyField.getText().trim());
        } catch (NumberFormatException e) {
            statusLabel.setText("价格/数量格式错误");
            return;
        }
        if (quantity <= 0) {
            statusLabel.setText("数量必须为正整数");
            return;
        }

        try {
            List<Trade> trades = engine.submitOrder(symbol, side, type, price, quantity, human);
            statusLabel.setText(trades.isEmpty()
                    ? "已挂单（未成交）"
                    : "成交 " + trades.size() + " 笔：" + describe(trades));
        } catch (OrderRejectedException e) {
            statusLabel.setText("拒绝：" + e.getMessage());
        } catch (IllegalArgumentException e) {
            statusLabel.setText("错误：" + e.getMessage());
        }
    }

    private String describe(List<Trade> trades) {
        Trade first = trades.get(0);
        return first.getPrice() + " × " + first.getQuantity() + (trades.size() > 1 ? " …" : "");
    }

    private void refresh() {
        String symbol = symbolBox.getValue();
        if (symbol == null) {
            return;
        }

        List<DepthRow> rows = new ArrayList<>();
        OrderBook book = engine.getBook(symbol);
        if (book != null) {
            for (PriceLevel level : book.getAskLevels(5)) {
                rows.add(new DepthRow("卖", level.price(), level.quantity()));
            }
            for (PriceLevel level : book.getBidLevels(5)) {
                rows.add(new DepthRow("买", level.price(), level.quantity()));
            }
        }
        depthTable.getItems().setAll(rows);

        Map<String, Double> prices = simulation.getMarketData().priceSnapshot();
        List<AccountRow> accRows = new ArrayList<>();
        for (Account account : simulation.getAccounts().all()) {
            int shares = account.getHoldingsSnapshot().values().stream().mapToInt(Integer::intValue).sum();
            double equity = account.computeEquity(prices);
            double ret = account.computeReturnRate(prices);
            accRows.add(new AccountRow(account.getAgentId(),
                    agentTypes.getOrDefault(account.getAgentId(), "?"),
                    account.getCash(), shares, equity, ret * 100));
        }
        accountTable.getItems().setAll(accRows);

        priceSeries.getData().clear();
        int i = 0;
        for (MarketData.PricePoint point : simulation.getMarketData().getHistory(symbol)) {
            priceSeries.getData().add(new XYChart.Data<>(i++, point.price()));
        }
    }

    /** 盘口行。 */
    public static class DepthRow {
        private final String side;
        private final double price;
        private final int qty;

        public DepthRow(String side, double price, int qty) {
            this.side = side;
            this.price = price;
            this.qty = qty;
        }

        public String getSide() {
            return side;
        }

        public double getPrice() {
            return price;
        }

        public int getQty() {
            return qty;
        }
    }

    /** 账户行。 */
    public static class AccountRow {
        private final String agent;
        private final String type;
        private final double cash;
        private final int shares;
        private final double equity;
        private final double returnPct;

        public AccountRow(String agent, String type, double cash, int shares, double equity, double returnPct) {
            this.agent = agent;
            this.type = type;
            this.cash = cash;
            this.shares = shares;
            this.equity = equity;
            this.returnPct = returnPct;
        }

        public String getAgent() {
            return agent;
        }

        public String getType() {
            return type;
        }

        public double getCash() {
            return cash;
        }

        public int getShares() {
            return shares;
        }

        public double getEquity() {
            return equity;
        }

        public double getReturnPct() {
            return returnPct;
        }
    }
}
