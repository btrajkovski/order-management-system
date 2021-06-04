package com.btrajkovski;

import akka.NotUsed;
import akka.actor.typed.ActorSystem;
import akka.actor.typed.Behavior;
import akka.actor.typed.javadsl.Behaviors;
import akka.http.javadsl.Http;
import akka.http.javadsl.ServerBinding;
import akka.http.javadsl.server.Route;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import com.btrajkovski.orders.OrderEntity2;
import com.btrajkovski.router.OrderRoutes;

import java.net.InetSocketAddress;
import java.util.concurrent.CompletionStage;

//#main-class
public class QuickstartApp {
    // #start-http-server
    static void startHttpServer(Route route, ActorSystem<?> system) {
        CompletionStage<ServerBinding> futureBinding =
                Http.get(system).newServerAt("localhost", 8080).bind(route);

        futureBinding.whenComplete((binding, exception) -> {
            if (binding != null) {
                InetSocketAddress address = binding.localAddress();
                system.log().info("Server online at http://{}:{}/",
                        address.getHostString(),
                        address.getPort());
            } else {
                system.log().error("Failed to bind HTTP endpoint, terminating system", exception);
                system.terminate();
            }
        });
    }
    // #start-http-server

    public static void main(String[] args) throws Exception {
        //#server-bootstrapping


        Behavior<NotUsed> rootBehavior = Behaviors.setup(context -> {

            AkkaManagement.get(context.getSystem()).start();
            ClusterBootstrap.get(context.getSystem()).start();
            OrderEntity2.init(context.getSystem());

            OrderRoutes orderRoutes = new OrderRoutes(context.getSystem());
            startHttpServer(orderRoutes.userRoutes(), context.getSystem());

            return Behaviors.empty();
        });

        // boot up server using the route as defined below
        ActorSystem.create(rootBehavior, "HelloAkkaHttpServer");
        //#server-bootstrapping
    }

}
//#main-class


