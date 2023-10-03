package com.kuhakupixel.atg.backend

import android.content.Context
import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap

/**
 * to communicate with ACE's engine binary
 * sending input and getting output
 */
class ACE(context: Context) {
    /**
     * thrown when an operation requires attach to a process
     * but we haven't
     */
    inner class NoAttachException : RuntimeException {
        constructor() : super() {}
        constructor(msg: String?) : super(msg) {}
    }

    /**
     * thrown when trying to attach when we have attached to a process
     * without deattaching first
     */
    inner class AttachingInARowException : RuntimeException {
        constructor() : super() {}
        constructor(msg: String?) : super(msg) {}
    }

    enum class Operator {
        greater, less, equal, greaterEqual, lessEqual, notEqual, unknown
    }

    enum class NumType {
        _int, _long, _short, _float, _byte;


        @Override
        override fun toString(): String {
            return this.name.replace("_", "")
        }

        companion object {
            fun fromString(s: String): NumType {
                var s = s
                if (s[0] != '_') s = "_$s"
                return valueOf(s)
            }
        }
    }

    inner class MatchInfo(var address: String, var prevValue: String)

    /**
     * the running server thread
     *
     *
     * if null means it isn't attached to anything
     */
    private var serverThread: Thread? = null

    /**
     * used for use cases that are unrelated to a specific process
     * for example, listing running processes, checking if a certain program is running
     * and etc
     */
    private val aceUtilClient: ACEUtilClient

    /**
     * used when attached to process, to scan and edit its memory
     */
    private var aceAttachClient: ACEAttachClient? = null
    private val context: Context
    private val availableNumTypes: List<NumTypeInfo>

    //
    private var statusPublisherPort: Int? = null

    @Synchronized
    fun getStatusPublisherPort(): Int? {
        return statusPublisherPort
    }

    init {
        this.context = context
        aceUtilClient = ACEUtilClient(context)
        availableNumTypes = GetAvailableNumTypes()
    }

    @Synchronized
    fun IsAttached(): Boolean {
        return aceAttachClient != null
    }

    @Synchronized
    private fun AssertAttached() {
        if (!IsAttached()) throw NoAttachException("Operation requires attaching to a process, but it hasn't been attached")
    }

    @Synchronized
    private fun AssertNoAttachInARow() {
        if (IsAttached()) throw AttachingInARowException("Cannot Attach without DeAttaching first")
    }

    // TODO: add statusPublisherPort as parameter
    @Synchronized

    fun ConnectToACEServer(port: Int, publisherPort: Int) {
        AssertNoAttachInARow()
        this.statusPublisherPort = publisherPort
        aceAttachClient = ACEAttachClient(port)
    }

    /**
     * this will create an ACE's server that is attached to process [pid]
     */
    @Synchronized

    fun Attach(pid: Long) {
        AssertNoAttachInARow()
        // start the server
        val ports: List<Int> = Port.GetOpenPorts(2)
        serverThread = ACEServer.GetStarterThread(context, pid, ports[0], ports[1])
        serverThread!!.start()
        ConnectToACEServer(ports[0], ports[1])
    }

    @Synchronized
    fun DeAttach() {
        AssertAttached()
        // tell server to die
        aceAttachClient!!.Request(arrayOf("stop"))
        aceAttachClient!!.close()
        aceAttachClient = null
        // only stop the server if we start one
        if (serverThread != null) {
            // wait for server's thread to finish
            // to make sure we are not attached anymore
            serverThread!!.join()
        }
    }

    @Synchronized
    fun GetNumTypeBitSize(numType: NumType): Int? {
        var bitSize: Int? = null
        for (typeInfo in availableNumTypes) {
            if (typeInfo.GetName().equals(numType.toString())) bitSize = typeInfo.GetBitSize()
        }
        return bitSize
    }

    @Synchronized
    fun GetNumTypeAndBitSize(numType: NumType): String {
        val bitSize: Int? = GetNumTypeBitSize(numType)
        return String.format("%s (%d bit)", numType.toString(), bitSize)
    }

    // =============== this commands require attach ===================
    @Synchronized
    fun CheaterCmd(cmd: Array<String>): String {
        AssertAttached()
        return aceAttachClient!!.Request(cmd)
    }

    @Synchronized
    fun CheaterCmdAsList(cmd: Array<String>): List<String> {
        AssertAttached()
        return aceAttachClient!!.RequestAsList(cmd)
    }

    @Synchronized
    fun GetAttachedPid(): Long {
        val pidStr = CheaterCmd(arrayOf("pid"))
        return pidStr.toLong()
    }

    @Synchronized
    fun SetNumType(type: NumType) {
        CheaterCmd(arrayOf("config", "type", type.toString()))
    }

    /**
     * get current type that ACE use
     */
    @Synchronized
    fun GetNumType(): NumType {
        val typeStr = CheaterCmd(arrayOf("config", "type"))
        return NumType.fromString(typeStr)
    }

    /**
     * run code/function when type is set to [numType]
     * after done, the type will be set to the previous one
     */
    @Synchronized
    fun ActionOnType(numType: NumType, action: () -> Unit) {
        val prevType = GetNumType()
        // set type first before writing
        if (prevType != numType) SetNumType(numType)
        action()
        if (prevType != numType) SetNumType(prevType)
    }

    @Synchronized
    fun ScanAgainstValue(operator: Operator, numValStr: String) {
        CheaterCmd(arrayOf("scan", operatorEnumToSymbolBiMap.get(operator)!!, numValStr))
    }

    @Synchronized
    fun ScanWithoutValue(operator: Operator) {
        CheaterCmd(arrayOf("filter", operatorEnumToSymbolBiMap.get(operator)!!))
    }

    @Synchronized
    fun WriteValueAtAddress(numType: NumType, address: String, value: String) {
        ActionOnType(numType) {
            this.CheaterCmd(arrayOf("writeat", address, value))
        }
    }

    @Synchronized
    fun FreezeAtAddress(numType: NumType, address: String) {
        ActionOnType(numType) {
            this.CheaterCmd(arrayOf("freeze at", address))
        }
    }

    @Synchronized
    fun FreezeValueAtAddress(numType: NumType, address: String, value: String) {
        ActionOnType(numType) {
            this.CheaterCmd(
                arrayOf(
                    "freeze at",
                    address,
                    "--value",
                    value
                )
            )
        }
    }

    @Synchronized
    fun UnFreezeAtAddress(numType: NumType, address: String) {
        ActionOnType(numType) {
            this.CheaterCmd(arrayOf("unfreeze at", address))
        }
    }

    @Synchronized
    fun GetMatchCount(): Int {
        return CheaterCmd(arrayOf<String>("matchcount")).toInt()
    }

    @Synchronized
    fun ResetMatches() {
        CheaterCmd(arrayOf("reset"))
    }

    @Synchronized
    fun ListMatches(maxCount: Int): List<MatchInfo> {
        /**
         * get list of matches with list command
         * which will return a list of [address] - [prev value] one per each line
         */
        val matches: MutableList<MatchInfo> = mutableListOf<MatchInfo>()
        val matchesStr = CheaterCmdAsList(arrayOf("list", "--max-count", maxCount.toString()))
        for (s: String in matchesStr) {
            val splitted: List<String> = s.split(" ")
            if (splitted.size != 2) {
                throw IllegalArgumentException(
                    String.format(
                        "unexpected Output when listing matches: \"%s\"",
                        s
                    )
                )
            }
            matches.add(MatchInfo(splitted[0], splitted[1]))
        }
        return matches
    }

    // =============== this commands don't require attach ===================
    @Synchronized
    fun UtilCmdAsList(cmd: Array<String>): List<String> {
        return aceUtilClient.RequestAsList(cmd)
    }

    @Synchronized
    fun UtilCmd(cmd: Array<String>): String {
        return aceUtilClient.Request(cmd)
    }

    @Synchronized
    fun ListRunningProc(): List<ProcInfo> {
        val runningProcs: MutableList<ProcInfo> = mutableListOf()
        // use --reverse so newest process will be shown first
        val runningProcsInfoStr = UtilCmdAsList(arrayOf("ps", "ls", "--reverse"))
        // parse each string
        for (procInfoStr in runningProcsInfoStr) {
            runningProcs.add(ProcInfo(procInfoStr))
        }
        return runningProcs
    }

    @Synchronized
    fun IsPidRunning(pid: Long): Boolean {
        val boolStr = UtilCmd(arrayOf("ps", "is_running", pid.toString()))
        return boolStr.toBooleanStrict()
    }

    /**
     * Get List of available number types and its bit size
     * that ACE engine support, with command `type size`
     * which will return list of "<type name> <bit size>"
     * like "int 32", "short 16" and ect
    </bit></type> */
    @Synchronized
    fun GetAvailableNumTypes(): List<NumTypeInfo> {
        val numTypeInfos: MutableList<NumTypeInfo> = mutableListOf()
        val out = UtilCmdAsList(arrayOf("info", "type"))
        for (s in out) {
            val splitted: List<String> = s.split(" ")
            assert(2 == splitted.size)
            val typeStr = splitted[0]
            val bitSize: Int = splitted[1].toInt()
            numTypeInfos.add(NumTypeInfo(typeStr, bitSize))
        }
        return numTypeInfos
    }

    @Synchronized
    fun GetAvailableOperatorTypes(): List<Operator> {
        // the output will be a list of supported operators like
        // >
        // <
        // >=
        // etc
        val availableOperators: MutableList<Operator> = mutableListOf()
        val out = UtilCmdAsList(arrayOf("info", "operator"))
        for (s in out) availableOperators.add(operatorEnumToSymbolBiMap.inverse().get(s)!!)
        return availableOperators
    }

    companion object {
        // https://stackoverflow.com/a/507658/14073678
        val operatorEnumToSymbolBiMap: BiMap<Operator, String> = HashBiMap.create()

        init {
            operatorEnumToSymbolBiMap.put(Operator.greater, ">")
            operatorEnumToSymbolBiMap.put(Operator.less, "<")
            operatorEnumToSymbolBiMap.put(Operator.equal, "=")
            operatorEnumToSymbolBiMap.put(Operator.greaterEqual, ">=")
            operatorEnumToSymbolBiMap.put(Operator.lessEqual, "<=")
            operatorEnumToSymbolBiMap.put(Operator.notEqual, "!=")
            operatorEnumToSymbolBiMap.put(Operator.unknown, "?")
        }
    }
}