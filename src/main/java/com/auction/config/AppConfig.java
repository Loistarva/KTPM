package com.auction.config;
import com.auction.domain.repository.AuctionRepository;
import com.auction.domain.service.AuctionService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AppConfig {

    // Tiêm Implementation (Infrastructure) vào Interface (Domain)
    @Bean
    public AuctionService auctionService(AuctionRepository auctionRepository) {
        return new AuctionService(auctionRepository);
    }
}