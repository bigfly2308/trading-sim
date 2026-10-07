package com.cufe.cs.trading.io;

import com.cufe.cs.trading.core.MarketListener;
import com.cufe.cs.trading.model.Order;
import com.cufe.cs.trading.model.Trade;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/** 将委托与成交追加写入 CSV 日志（线程安全，随用随刷）。 */
public class CsvLogger implements MarketListener, AutoCloseable {
    private final BufferedWriter orderWriter;
    private final BufferedWriter tradeWriter;
    private final Object lock = new Object();

    public CsvLogger(Path directory) {
        try {
            Files.createDirectories(directory);
            Path orderFile = directory.resolve("orders.csv");
            Path tradeFile = directory.resolve("trades.csv");
            boolean orderExists = Files.exists(orderFile);
            boolean tradeExists = Files.exists(tradeFile);
            orderWriter = Files.newBufferedWriter(orderFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            tradeWriter = Files.newBufferedWriter(tradeFile, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (!orderExists) {
                orderWriter.write("orderId,agentId,symbol,side,type,price,quantity,remaining,status\n");
            }
            if (!tradeExists) {
                tradeWriter.write("tradeId,symbol,price,quantity,buyAgentId,sellAgentId\n");
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void onOrder(Order order) {
        String price = order.isMarket() ? "" : String.format("%.2f", order.getPrice());
        String line = String.format("%s,%s,%s,%s,%s,%s,%d,%d,%s%n",
                order.getOrderId(), order.getAgentId(), order.getSymbol(), order.getSide(),
                order.getType(), price, order.getQuantity(), order.getRemaining(), order.getStatus());
        write(orderWriter, line);
    }

    @Override
    public void onTrade(Trade trade) {
        String line = String.format("%d,%s,%.2f,%d,%s,%s%n",
                trade.getId(), trade.getSymbol(), trade.getPrice(), trade.getQuantity(),
                trade.getBuyAgentId(), trade.getSellAgentId());
        write(tradeWriter, line);
    }

    private void write(BufferedWriter writer, String line) {
        synchronized (lock) {
            try {
                writer.write(line);
                writer.flush();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Override
    public void close() {
        synchronized (lock) {
            try {
                orderWriter.close();
                tradeWriter.close();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }
}
