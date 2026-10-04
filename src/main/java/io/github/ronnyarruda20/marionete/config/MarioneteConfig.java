package io.github.ronnyarruda20.marionete.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.github.ronnyarruda20.marionete.adb.Adb;
import io.github.ronnyarruda20.marionete.adb.AdbProcesso;
import io.github.ronnyarruda20.marionete.adb.Celular;
import io.github.ronnyarruda20.marionete.governo.Confirmador;
import io.github.ronnyarruda20.marionete.governo.Fios;
import io.github.ronnyarruda20.marionete.governo.Guarda;
import io.github.ronnyarruda20.marionete.governo.Registro;
import io.github.ronnyarruda20.marionete.governo.TravaDeSessao;

@Configuration
public class MarioneteConfig {

    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    Adb adb(MarioneteProperties props) {
        return new AdbProcesso(props.adb());
    }

    @Bean
    Celular celular(Adb adb, MarioneteProperties props) {
        return new Celular(adb, props.serial());
    }

    @Bean
    Fios fios(MarioneteProperties props, Clock relogio) {
        return new Fios(props.pasta(), relogio);
    }

    @Bean
    Registro registro(MarioneteProperties props, Clock relogio) {
        return new Registro(props.pasta(), relogio);
    }

    @Bean(destroyMethod = "soltar")
    TravaDeSessao travaDeSessao(MarioneteProperties props, Clock relogio) {
        return new TravaDeSessao(props.pasta(), props.travaExpiraEm(), relogio);
    }

    @Bean
    Confirmador confirmador() {
        return new Confirmador();
    }

    @Bean
    Guarda guarda(MarioneteProperties props, Celular celular, Fios fios, TravaDeSessao trava, Registro registro,
            Confirmador confirmador) {
        return new Guarda(props.modoEfetivo(), props.negadosEfetivos(), celular, fios, trava, registro, confirmador);
    }
}
