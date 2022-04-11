package com.portto.solana.web3.rpc

/**
 * Created by guness on 6.12.2021 19:45
 */
data class WeightedCluster(val endpoints: List<WeightedEndpoint>) {

    /**
     * Returns RPC Endpoint based on a list of weighted endpoints
     * Weighted endpoints can be given a integer weight, with higher weights used more than lower weights
     * Total weights across all endpoints do not need to sum up to any specific number
     * @return String RPCEndpoint
     */
    fun getWeightedEndpoint(): String {
        var currentNumber = 0
        val randomMultiplier = endpoints.map(WeightedEndpoint::weight).sum()
        val randomNumber = Math.random() * randomMultiplier
        val currentEndpoint = ""
        for (endpoint in endpoints) {
            if (randomNumber > currentNumber + endpoint.weight) {
                currentNumber += endpoint.weight
            } else if (randomNumber >= currentNumber && randomNumber <= currentNumber + endpoint.weight) {
                return endpoint.url
            }
        }
        return currentEndpoint
    }
}
