package com.quantlime.stock.service;

import com.quantlime.stock.domain.ListingStatus;
import com.quantlime.stock.domain.MarketType;
import com.quantlime.stock.domain.Stock;
import com.quantlime.stock.repository.StockRepository;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Component
@Profile("!test")
@RequiredArgsConstructor
public class StockMasterInitializer implements ApplicationRunner {

    private static final String CSV_FILE_PATH = "data/krx-stocks.csv";

    private final StockRepository stockRepository;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (stockRepository.count() > 0) {
            log.info("종목 마스터 이미 적재됨: count={}",
                stockRepository.count());
            return;
        }

        ClassPathResource resource = new ClassPathResource(CSV_FILE_PATH);
        if (!resource.exists()) {
            log.warn("종목 마스터 CSV 파일 없음: path={}", CSV_FILE_PATH);
            return;
        }

        try (InputStream is = resource.getInputStream()) {
            List<Stock> stocks = parseCsv(is);
            stockRepository.saveAll(stocks);
            log.info("종목 마스터 적재 완료: count={}", stocks.size());
        } catch (Exception e) {
            log.error("종목 마스터 적재 실패: error={}", e.getMessage(), e);
        }
    }

    private List<Stock> parseCsv(InputStream inputStream) {
        List<Stock> stocks = new ArrayList<>();

        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {

            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {
                String[] fields = line.split(",");
                if (fields.length < 4) {
                    continue;
                }

                String stockCode = fields[0].trim();
                String stockName = fields[1].trim();
                String marketLabel = fields[2].trim();
                String sector = fields[3].trim();

                MarketType marketType = parseMarketType(marketLabel);
                if (marketType == null) {
                    continue;
                }

                stocks.add(Stock.of(
                    stockCode,
                    stockName,
                    marketType,
                    ListingStatus.LISTED,
                    sector
                ));
            }
        } catch (Exception e) {
            log.error("CSV 파싱 실패: error={}", e.getMessage(), e);
        }

        return stocks;
    }

    private MarketType parseMarketType(String marketLabel) {
        try {
            return MarketType.of(marketLabel);
        } catch (Exception e) {
            return null;
        }
    }
}
