/*
 * The MIT License
 *
 * Copyright 2017 Lars Kroll <lkroll@kth.se>.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package se.kth.id2203.overlay;

import com.larskroll.common.collections._
import se.kth.id2203.bootstrapping.NodeAssignment
import se.kth.id2203.networking.NetAddress

import scala.collection.immutable.SortedSet
import com.roundeights.hasher.Hasher

@SerialVersionUID(0x57bdfad1eceeeaaeL)
class LookupTable extends NodeAssignment with Serializable {


  val partitions: TreeSetMultiMap[String, NetAddress] = TreeSetMultiMap.empty[String, NetAddress];

  // Returns the network address of nodes in one partition which are responsible for key
  def lookup(key: String): Iterable[NetAddress] = {
    val keyHash = LookupTable.getHash(key);
    val partition = partitions.floor(keyHash) match {
      case Some(k) => k
      case None    => partitions.lastKey
    }
    partitions(partition);
  }

  // Gets the nodes of all partitions
  def getNodes: Set[NetAddress] = partitions.foldLeft(Set.empty[NetAddress]) {
    case (acc, kv) => acc ++ kv._2
  }

  def getNodes(node: NetAddress): Set[NetAddress] = partitions
    .filter(x => x._2.iterator.contains(node))
    .foldLeft(Set.empty[NetAddress]) {
      case (acc, kv) => acc ++ kv._2
    }

  def isFirst(node: NetAddress): Boolean = {
    LookupTable.getSortedSet(getNodes(node)).head._2.equals(node)
  }

  def getPartitionBoundaries(node: NetAddress): (String, String) = {
    // lower boundary is inclusive, upper boundary is exclusive!
    val nodeHash = LookupTable.getHash(node.toString());

    val lower = partitions.floor(nodeHash) match {
      case Some(k) => k
      case None    => partitions.lastKey
    }

    val upper = partitions.ceil(nodeHash) match {
      case Some(k) => k
      case None    => partitions.firstKey
    }

    (lower, upper)
  }

  override def toString: String = {
    val sb = new StringBuilder();
    sb.append("LookupTable(\n");
    sb.append(partitions.mkString(","));
    sb.append(")");
    sb.toString();
  }
}

object LookupTable {
  //takes the node addresses and creates lookup table
  def generate(nodes: Set[NetAddress]): LookupTable = {
    var sortedNodes: SortedSet[(String, NetAddress)] = getSortedSet(nodes);

    // todo: move k to reference.conf? currently cfg is not accessible from here
    val k: Int = 3;             // min partition size
    val n: Int = nodes.size;    // node count
    var p: Int = 1;             // partition count
    var lon: Int = 0;           // left over nodes
    var pn: Int = 0;            // plus nodes
    var pnc: Int = 0;           // plus nodes count

    if (n >= k) {
      p = Math.floor(n.toDouble / k.toDouble).toInt;
      lon = n - p * k;
      pn = Math.ceil(lon.toDouble / p.toDouble).toInt;
      pnc = if (lon > 0 && pn > 0) lon / pn else 0;
    }

    val lut = new LookupTable();

    // go through partitions
    for(i <- 0 until p) {
      var partitionNodes = Set.empty[NetAddress];
      var hash = "";

      // go through nodes in this partition
      val x = if (n < k) n else if (i < pnc) k+pn else k;
      for(j <- 0 until x) {
        val cur = sortedNodes.head;
        sortedNodes = sortedNodes.tail;

        // add nodes to partition and set first node's hash as partition hash
        partitionNodes += cur._2;
        if (j == 0) {
          hash = cur._1;
        }
      }

      lut.partitions ++= (hash -> partitionNodes);
    }
    assert(sortedNodes.isEmpty, "not all nodes were sorted into lookup table");

    lut
  }

  def getHash(key: String): String = {
    Hasher(key).sha512;
  }

  // returns list of all nodes ordered by their hash
  def getSortedSet(nodes: Set[NetAddress]): SortedSet[(String, NetAddress)] = {
    val order = Ordering.by { x: (String, NetAddress) => x._1 };
    var sortedNodes: SortedSet[(String, NetAddress)] = SortedSet.empty(order);

    // fill list of all nodes by generating hashes
    sortedNodes = sortedNodes ++ nodes.map(x => {
      (getHash(x.toString()), x);
    });

    sortedNodes
  }
}
